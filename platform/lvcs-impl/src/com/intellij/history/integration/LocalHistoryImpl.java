// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration;

import com.intellij.history.*;
import com.intellij.history.core.*;
import com.intellij.history.integration.ui.models.DirectoryHistoryDialogModel;
import com.intellij.history.integration.ui.models.EntireFileHistoryDialogModel;
import com.intellij.history.integration.ui.models.HistoryDialogModel;
import com.intellij.history.utils.LocalHistoryLog;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.FlushingDaemon;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.PathKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.history.integration.LocalHistoryUtil.findRevisionIndexToRevert;

public final class LocalHistoryImpl extends LocalHistory implements Disposable {
  private static final String DAYS_TO_KEEP = "localHistory.daysToKeep";

  private int myDaysToKeep = AdvancedSettings.getInt(DAYS_TO_KEEP);
  private boolean myDisabled;
  private ChangeList myChangeList;
  private LocalHistoryFacade myVcs;
  private IdeaGateway myGateway;
  private ScheduledFuture<?> myFlusherTask;

  private @Nullable LocalHistoryEventDispatcher myEventDispatcher;

  private final AtomicBoolean isInitialized = new AtomicBoolean();

  @NotNull
  public static LocalHistoryImpl getInstanceImpl() {
    return (LocalHistoryImpl)getInstance();
  }

  public LocalHistoryImpl() {
    init();
  }

  private void init() {
    Application app = ApplicationManager.getApplication();
    if (!app.isUnitTestMode() && app.isHeadlessEnvironment()) {
      return;
    }

    // too early for Registry
    if (SystemProperties.getBooleanProperty("lvcs.disable.local.history", false)) {
      LocalHistoryLog.LOG.warn("Local history is disabled");
      myDisabled = true;
      return;
    }

    // initialize persistent fs
    @SuppressWarnings("unused")
    PersistentFS instance = PersistentFS.getInstance();

    ShutDownTracker.getInstance().registerShutdownTask(() -> doDispose());

    initHistory();
    app.getMessageBus().simpleConnect().subscribe(AdvancedSettingsChangeListener.TOPIC, new AdvancedSettingsChangeListener() {
      @Override
      public void advancedSettingChanged(@NotNull String id, @NotNull Object oldValue, @NotNull Object newValue) {
        if (id.equals(DAYS_TO_KEEP)) {
          myDaysToKeep = (int) newValue;
        }
      }
    });
    myFlusherTask = FlushingDaemon.everyFiveSeconds(() -> {
      myChangeList.force();
    });
    isInitialized.set(true);
  }

  private void initHistory() {
    ChangeListStorage storage;
    try {
      storage = new ChangeListStorageImpl(getStorageDir());
    }
    catch (Throwable e) {
      LocalHistoryLog.LOG.warn("cannot create storage, in-memory  implementation will be used", e);
      storage = new InMemoryChangeListStorage();
    }

    myChangeList = new ChangeList(storage);
    myVcs = new LocalHistoryFacade(myChangeList);

    myGateway = new IdeaGateway();

    myEventDispatcher = new LocalHistoryEventDispatcher(myVcs, myGateway);
  }

  @Nullable
  LocalHistoryEventDispatcher getEventDispatcher() {
    return myEventDispatcher;
  }

  public static @NotNull Path getStorageDir() {
    return Paths.get(PathManager.getSystemPath(), "LocalHistory");
  }

  @Override
  public void dispose() {
    doDispose();
  }

  private void doDispose() {
    if (!isInitialized.getAndSet(false)) return;

    myFlusherTask.cancel(false);
    myFlusherTask = null;

    // TODO(vadim.salavatov): purging at disposal might affect shutdown time, maybe we should move it to init()?
    purgeObsolete(); // flushes in the end
    myChangeList.close();
    LocalHistoryLog.LOG.debug("Local history storage successfully closed.");
  }

  private void purgeObsolete() {
    long period = myDaysToKeep * 1000L * 60L * 60L * 24L;
    LocalHistoryLog.LOG.debug("Purging local history...");
    myChangeList.purgeObsolete(period);
  }

  @TestOnly
  public void cleanupForNextTest() {
    doDispose();
    PathKt.delete(getStorageDir());
    init();
  }

  @Override
  public LocalHistoryAction startAction(@NlsContexts.Label String name) {
    if (!isInitialized()) return LocalHistoryAction.NULL;

    LocalHistoryActionImpl a = new LocalHistoryActionImpl(myEventDispatcher, name);
    a.start();
    return a;
  }

  @Override
  public Label putUserLabel(@NotNull Project p, @NotNull @NlsContexts.Label String name) {
    if (!isInitialized()) return Label.NULL_INSTANCE;
    myGateway.registerUnsavedDocuments(myVcs);
    return label(myVcs.putUserLabel(name, getProjectId(p)));
  }

  private static String getProjectId(Project p) {
    return p.getLocationHash();
  }

  @Override
  public Label putSystemLabel(@NotNull Project p, @NotNull @NlsContexts.Label String name, int color) {
    if (!isInitialized()) return Label.NULL_INSTANCE;
    myGateway.registerUnsavedDocuments(myVcs);
    return label(myVcs.putSystemLabel(name, getProjectId(p), color));
  }

  @ApiStatus.Internal
  public void addVFSListenerAfterLocalHistoryOne(BulkFileListener virtualFileListener, Disposable disposable) {
    myEventDispatcher.addVirtualFileListener(virtualFileListener, disposable);
  }

  private Label label(final LabelImpl impl) {
    return new Label() {
      @Override
      public void revert(@NotNull Project project, @NotNull VirtualFile file) throws LocalHistoryException {
        revertToLabel(project, file, impl);
      }

      @Override
      public ByteContent getByteContent(final String path) {
        return ReadAction.compute(() -> impl.getByteContent(myGateway.createTransientRootEntryForPathOnly(path), path));
      }
    };
  }

  @Override
  public byte @Nullable [] getByteContent(@NotNull VirtualFile f, @NotNull FileRevisionTimestampComparator c) {
    if (!isInitialized()) return null;
    return ReadAction.compute(() -> {
      if (!myGateway.areContentChangesVersioned(f)) return null;
      return new ByteContentRetriever(myGateway, myVcs, f, c).getResult();
    });
  }

  @Override
  public boolean isUnderControl(@NotNull VirtualFile f) {
    return isInitialized() && myGateway.isVersioned(f);
  }

  private boolean isInitialized() {
    return isInitialized.get();
  }

  public boolean isDisabled() {
    return myDisabled;
  }

  @Nullable
  public LocalHistoryFacade getFacade() {
    return myVcs;
  }

  @Nullable
  public IdeaGateway getGateway() {
    return myGateway;
  }

  private void revertToLabel(@NotNull Project project, @NotNull VirtualFile f, @NotNull LabelImpl impl) throws LocalHistoryException {
    HistoryDialogModel dirHistoryModel = f.isDirectory()
                                         ? new DirectoryHistoryDialogModel(project, myGateway, myVcs, f)
                                         : new EntireFileHistoryDialogModel(project, myGateway, myVcs, f);
    int leftRev = findRevisionIndexToRevert(dirHistoryModel, impl);
    if (leftRev < 0) {
      throw new LocalHistoryException("Couldn't find label revision");
    }
    if (leftRev == 0) return; // we shouldn't revert because no changes found to revert;
    try {
      dirHistoryModel.selectRevisions(-1, leftRev - 1); //-1 because we should revert all changes up to previous one, but not label-related.
      dirHistoryModel.createReverter().revert();
    }
    catch (Exception e) {
      throw new LocalHistoryException(String.format("Couldn't revert %s to local history label.", f.getName()), e);
    }
  }
}