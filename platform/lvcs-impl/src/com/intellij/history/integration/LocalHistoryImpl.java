// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.history.integration.LocalHistoryUtil.findRevisionIndexToRevert;

public final class LocalHistoryImpl extends LocalHistory implements Disposable {
  private MessageBusConnection myConnection;
  private ChangeList myChangeList;
  private LocalHistoryFacade myVcs;
  private IdeaGateway myGateway;

  private LocalHistoryEventDispatcher myEventDispatcher;

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

    // initialize persistent f
    @SuppressWarnings("unused")
    PersistentFS instance = PersistentFS.getInstance();

    ShutDownTracker.getInstance().registerShutdownTask(() -> doDispose());

    initHistory();
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

    myConnection = ApplicationManager.getApplication().getMessageBus().connect(this);
    myConnection.subscribe(VirtualFileManager.VFS_CHANGES, myEventDispatcher);
    myConnection.subscribe(CommandListener.TOPIC, myEventDispatcher);

    VirtualFileManager.getInstance().addVirtualFileManagerListener(myEventDispatcher, this);
  }

  @NotNull
  public static File getStorageDir() {
    return new File(getSystemPath(), "LocalHistory");
  }

  private static String getSystemPath() {
    return PathManager.getSystemPath();
  }

  @Override
  public void dispose() {
    doDispose();
  }

  private void doDispose() {
    if (!isInitialized.getAndSet(false)) return;

    long period = Registry.intValue("localHistory.daysToKeep") * 1000L * 60L * 60L * 24L;

    myConnection.disconnect();
    myConnection = null;

    LocalHistoryLog.LOG.debug("Purging local history...");
    myChangeList.purgeObsolete(period);
    myChangeList.close();
    LocalHistoryLog.LOG.debug("Local history storage successfully closed.");
  }

  @TestOnly
  public void cleanupForNextTest() {
    doDispose();
    FileUtil.delete(getStorageDir());
    init();
  }

  @Override
  public LocalHistoryAction startAction(String name) {
    if (!isInitialized()) return LocalHistoryAction.NULL;

    LocalHistoryActionImpl a = new LocalHistoryActionImpl(myEventDispatcher, name);
    a.start();
    return a;
  }

  @Override
  public Label putUserLabel(@NotNull Project p, @NotNull String name) {
    if (!isInitialized()) return Label.NULL_INSTANCE;
    myGateway.registerUnsavedDocuments(myVcs);
    return label(myVcs.putUserLabel(name, getProjectId(p)));
  }

  private static String getProjectId(Project p) {
    return p.getLocationHash();
  }

  @Override
  public Label putSystemLabel(@NotNull Project p, @NotNull String name, int color) {
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
    if (!myGateway.areContentChangesVersioned(f)) return null;
    return ReadAction.compute(() -> new ByteContentRetriever(myGateway, myVcs, f, c).getResult());
  }

  @Override
  public boolean isUnderControl(@NotNull VirtualFile f) {
    return isInitialized() && myGateway.isVersioned(f);
  }

  private boolean isInitialized() {
    return isInitialized.get();
  }

  @Nullable
  public LocalHistoryFacade getFacade() {
    return myVcs;
  }

  @Nullable
  public IdeaGateway getGateway() {
    return myGateway;
  }

  private void revertToLabel(@NotNull Project project, @NotNull VirtualFile f, @NotNull LabelImpl impl) throws LocalHistoryException{
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