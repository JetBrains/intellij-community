// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Progressive;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.UnknownSdkBalloonNotification.FixedSdksNotification;
import com.intellij.openapi.projectRoots.impl.UnknownSdkEditorNotification.FixableSdkNotification;
import com.intellij.openapi.roots.ui.configuration.*;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkResolver.UnknownSdkLookup;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.TripleFunction;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

import static com.intellij.openapi.progress.PerformInBackgroundOption.ALWAYS_BACKGROUND;

public class UnknownSdkTracker {
  private static final Logger LOG = Logger.getInstance(UnknownSdkTracker.class);

  @NotNull
  public static UnknownSdkTracker getInstance(@NotNull Project project) {
    return project.getService(UnknownSdkTracker.class);
  }

  @NotNull private final Project myProject;
  @NotNull private final MergingUpdateQueue myUpdateQueue;

  public UnknownSdkTracker(@NotNull Project project) {
    myProject = project;
    myUpdateQueue = new MergingUpdateQueue(getClass().getSimpleName(),
                                           700,
                                           true,
                                           null,
                                           myProject,
                                           null,
                                           false)
      .usePassThroughInUnitTestMode();
  }

  private static boolean isEnabled() {
    return Registry.is("unknown.sdk") && UnknownSdkResolver.EP_NAME.hasAnyExtensions();
  }

  public void updateUnknownSdksBlocking(@NotNull UnknownSdkCollector collector,
                                        @NotNull ShowStatusCallback showStatus) {
    if (!isEnabled()) {
      showStatus.showEmptyStatus();
      return;
    }

    ProgressManager.getInstance()
      .run(new Task.Modal(myProject, ProjectBundle.message("progress.title.resolving.sdks"), false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          var snapshot = collector.collectSdksBlocking();

          var action = createProcessSdksAction(snapshot, showStatus);
          if (action == null) return;

          action.run(indicator);
        }
      });
  }

  @NotNull
  private Update newUpdateTask(@NotNull ShowStatusCallback showStatus,
                               @NotNull Predicate<UnknownSdkSnapshot> shouldProcessSnapshot) {
    return new Update("update") {
      @Override
      public void run() {
        if (!isEnabled()) {
          showStatus.showEmptyStatus();
          return;
        }

        new UnknownSdkCollector(myProject)
          .collectSdksPromise(snapshot -> {
            if (!shouldProcessSnapshot.test(snapshot)) return;

            var action = createProcessSdksAction(snapshot, showStatus);
            if (action == null) return;

            ProgressManager.getInstance()
              .run(new Task.Backgroundable(myProject, ProjectBundle.message("progress.title.resolving.sdks"), false, ALWAYS_BACKGROUND) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                  action.run(indicator);
                }
              });
          });
      }
    };
  }

  private final Predicate<UnknownSdkSnapshot> myIsNewSnapshot = new Predicate<>() {
    private UnknownSdkSnapshot myPreviousRequestCache = null;

    @Override
    public boolean test(UnknownSdkSnapshot snapshot) {
      //there is nothing to do if we see the same snapshot, IDEA-236153
      if (snapshot.equals(myPreviousRequestCache)) return false;
      myPreviousRequestCache = snapshot;
      return true;
    }
  };

  public void updateUnknownSdksNow() {
    myUpdateQueue.run(newUpdateTask(new DefaultShowStatusCallbackAdapter(), myIsNewSnapshot));
  }

  public void updateUnknownSdks() {
    myUpdateQueue.queue(newUpdateTask(new DefaultShowStatusCallbackAdapter(), myIsNewSnapshot));
  }

  private static boolean allowFixesFor(@NotNull SdkTypeId type) {
    return UnknownSdkResolver.EP_NAME.findFirstSafe(it -> it.supportsResolution(type)) != null;
  }

  @NotNull
  private static <E extends UnknownSdk> List<E> filterOnlyAllowedEntries(@NotNull List<? extends E> input) {
    List<E> copy = new ArrayList<>();
    for (E item : input) {
      SdkType type = item.getSdkType();

      if (allowFixesFor(type)) {
        copy.add(item);
      }
    }

    return copy;
  }

  @NotNull
  private static List<Sdk> filterOnlyAllowedSdkEntries(@NotNull List<Sdk> input) {
    List<Sdk> copy = new ArrayList<>();
    for (Sdk item : input) {
      SdkTypeId type = item.getSdkType();

      if (allowFixesFor(type)) {
        copy.add(item);
      }
    }

    return copy;
  }

  @Nullable
  private Progressive createProcessSdksAction(@NotNull UnknownSdkSnapshot snapshot,
                                              @NotNull ShowStatusCallback showStatus) {
    //we cannot use snapshot#missingSdks here, because it affects other IDEs/languages where our logic is not good enough
    List<UnknownSdk> fixable = filterOnlyAllowedEntries(snapshot.getResolvableSdks());
    List<Sdk> usedSdks = filterOnlyAllowedSdkEntries(snapshot.getKnownSdks());

    if (fixable.isEmpty() && usedSdks.isEmpty()) {
      showStatus.showEmptyStatus();
      return null;
    }

    return indicator -> {
             try {
               List<UnknownInvalidSdk> invalidSdks = new ArrayList<>();
               Map<UnknownSdk, UnknownSdkLocalSdkFix> localFixes = new HashMap<>();
               Map<UnknownSdk, UnknownSdkDownloadableSdkFix> downloadFixes = new HashMap<>();

               if (!usedSdks.isEmpty()) {
                 indicator.pushState();
                 indicator.setText(ProjectBundle.message("progress.text.resolving.existing.sdks"));
                 invalidSdks = UnknownInvalidSdk.resolveInvalidSdks(usedSdks);
                 fixable.addAll(invalidSdks);
                 indicator.popState();
               }

               if (!fixable.isEmpty()) {
                 indicator.pushState();
                 indicator.setText(ProjectBundle.message("progress.text.resolving.missing.sdks"));
                 List<UnknownSdkLookup> lookups = collectSdkLookups(indicator);

                 if (!lookups.isEmpty()) {
                   indicator.setText(ProjectBundle.message("progress.text.looking.for.local.sdks"));
                   localFixes = findFixesAndRemoveFixable(indicator, fixable, lookups, UnknownSdkLookup::proposeLocalFix);

                   if (!fixable.isEmpty()) {
                     indicator.setText(ProjectBundle.message("progress.text.looking.for.downloadable.sdks"));
                     downloadFixes = findFixesAndRemoveFixable(indicator, fixable, lookups, UnknownSdkLookup::proposeDownload);
                   }
                 }

                 indicator.popState();
               }

               UnknownInvalidSdk.removeAndUpdate(invalidSdks, fixable, localFixes, downloadFixes);

               if (!localFixes.isEmpty()) {
                 indicator.pushState();
                 indicator.setText(ProjectBundle.message("progress.text.configuring.sdks"));
                 configureLocalSdks(localFixes);
                 indicator.popState();
               }

               showStatus.showStatus(fixable, localFixes, downloadFixes, invalidSdks);
             } catch (Throwable t) {
               if (t instanceof ControlFlowException) {
                 showStatus.showInterruptedStatus();
                 ExceptionUtil.rethrow(t);
               }

               LOG.warn("Failed to complete SDKs lookup. " + t.getMessage(), t);
               showStatus.showEmptyStatus();
             }
         };
  }

  public interface ShowStatusCallback {
    default void showInterruptedStatus() {
      showEmptyStatus();
    }

    default void showEmptyStatus() {
      showStatus(Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList());
    }

    void showStatus(@NotNull List<UnknownSdk> unknownSdksWithoutFix,
                    @NotNull Map<UnknownSdk, UnknownSdkLocalSdkFix> localFixes,
                    @NotNull Map<UnknownSdk, UnknownSdkDownloadableSdkFix> downloadFixes,
                    @NotNull List<UnknownInvalidSdk> invalidSdks);
  }

  public static abstract class ShowStatusCallbackAdapter implements ShowStatusCallback {
    protected final UnknownSdkBalloonNotification mySdkBalloonNotification;
    protected final UnknownSdkEditorNotification mySdkEditorNotification;

    protected ShowStatusCallbackAdapter(@NotNull Project project) {
      mySdkBalloonNotification = UnknownSdkBalloonNotification.getInstance(project);
      mySdkEditorNotification = UnknownSdkEditorNotification.getInstance(project);
    }

    @Override
    public final void showStatus(@NotNull List<UnknownSdk> unknownSdksWithoutFix,
                                 @NotNull Map<UnknownSdk, UnknownSdkLocalSdkFix> localFixes,
                                 @NotNull Map<UnknownSdk, UnknownSdkDownloadableSdkFix> downloadFixes,
                                 @NotNull List<UnknownInvalidSdk> invalidSdks) {
      var fixed = mySdkBalloonNotification.buildNotifications(localFixes);
      var actions = mySdkEditorNotification.buildNotifications(unknownSdksWithoutFix, downloadFixes, invalidSdks);
      notifySdks(fixed, actions);
    }

    /**
     * The notification callback that can be executed on any thread,
     * it can also be in the same call stack of the {@link #updateUnknownSdksBlocking(UnknownSdkCollector, ShowStatusCallback)}
     * method call, if no actions were found.
     */
    protected abstract void notifySdks(@NotNull FixedSdksNotification fixed,
                                       @NotNull FixableSdkNotification actions);
  }

  private class DefaultShowStatusCallbackAdapter extends ShowStatusCallbackAdapter {
    private DefaultShowStatusCallbackAdapter() {
      super(myProject);
    }

    @Override
    protected void notifySdks(@NotNull FixedSdksNotification fixed, @NotNull FixableSdkNotification actions) {
      mySdkBalloonNotification.notifyFixedSdks(fixed);
      mySdkEditorNotification.showNotifications(actions);
    }
  }

  @NotNull
  private List<UnknownSdkLookup> collectSdkLookups(@NotNull ProgressIndicator indicator) {
    List<UnknownSdkLookup> lookups = new ArrayList<>();
    UnknownSdkResolver.EP_NAME.forEachExtensionSafe(ext -> {
      UnknownSdkLookup resolver = ext.createResolver(myProject, indicator);
      if (resolver != null) {
        lookups.add(resolver);
      }
    });
    return lookups;
  }

  public void applyDownloadableFix(@NotNull UnknownSdk info, @NotNull UnknownSdkDownloadableSdkFix fix) {
    downloadFix(myProject, info, fix, sdk -> {}, sdk -> {
      if (sdk != null) {
        updateUnknownSdksNow();
      }
    });
  }

  @ApiStatus.Internal
  public static void downloadFix(@Nullable Project project,
                                 @NotNull UnknownSdk info,
                                 @NotNull UnknownSdkDownloadableSdkFix fix,
                                 @NotNull Consumer<? super Sdk> onSdkNameReady,
                                 @NotNull Consumer<? super Sdk> onCompleted) {
    UnknownSdkDownloader.downloadFix(project, info, fix,
                task -> {
                  String actualSdkName = info.getSdkName();
                  if (actualSdkName == null) {
                    actualSdkName = task.getSuggestedSdkName();
                  }
                  return ProjectJdkTable.getInstance().createSdk(actualSdkName, info.getSdkType());
                },
                onSdkNameReady,
                sdk -> {
                  if (sdk != null) {
                    fix.configureSdk(sdk);
                    registerNewSdkInJdkTable(sdk.getName(), sdk);
                  }
                  onCompleted.consume(sdk);
                });
  }

  @NotNull
  public EditorNotificationPanel.ActionHandler createSdkSelectionPopup(@Nullable String sdkName,
                                                                       @Nullable SdkType sdkType) {
    return SdkPopupFactory
      .newBuilder()
      .withProject(myProject)
      .withSdkTypeFilter(type -> sdkType == null || Objects.equals(type, sdkType))
      .onSdkSelected(sdk -> {
        registerNewSdkInJdkTable(sdkName, sdk);
        updateUnknownSdks();
      })
      .buildEditorNotificationPanelHandler();
  }

  private void configureLocalSdks(@NotNull Map<UnknownSdk, UnknownSdkLocalSdkFix> localFixes) {
    if (localFixes.isEmpty()) return;

    for (Map.Entry<UnknownSdk, UnknownSdkLocalSdkFix> e : localFixes.entrySet()) {
      UnknownSdk info = e.getKey();
      UnknownSdkLocalSdkFix fix = e.getValue();

      configureLocalSdk(info, fix, sdk -> {});
    }

    updateUnknownSdks();
  }

  @ApiStatus.Internal
  public static void configureLocalSdk(@NotNull UnknownSdk info,
                                       @NotNull UnknownSdkLocalSdkFix fix,
                                       @NotNull Consumer<? super Sdk> onCompleted) {
    ApplicationManager.getApplication().invokeLater(() -> {
      try {
        String actualSdkName = info.getSdkName();
        if (actualSdkName == null) {
          actualSdkName = fix.getSuggestedSdkName();
        }

        Sdk sdk = ProjectJdkTable.getInstance().createSdk(actualSdkName, info.getSdkType());
        SdkModificator mod = sdk.getSdkModificator();
        mod.setHomePath(FileUtil.toSystemIndependentName(fix.getExistingSdkHome()));
        mod.setVersionString(fix.getVersionString());
        mod.commitChanges();

        try {
          info.getSdkType().setupSdkPaths(sdk);
        }
        catch (Exception error) {
          LOG.warn("Failed to setupPaths for " + sdk + ". " + error.getMessage(), error);
        }
        fix.configureSdk(sdk);
        registerNewSdkInJdkTable(actualSdkName, sdk);
        LOG.info("Automatically set Sdk " + info + " to " + fix.getExistingSdkHome());
        onCompleted.consume(sdk);
      } catch (Exception error) {
        LOG.warn("Failed to configure " + info.getSdkType().getPresentableName() + " " + " for " + info + " for path " + fix + ". " + error.getMessage(), error);
        onCompleted.consume(null);
      }
    });
  }

  @NotNull
  private static <R> Map<UnknownSdk, R> findFixesAndRemoveFixable(@NotNull ProgressIndicator indicator,
                                                                  @NotNull List<UnknownSdk> infos,
                                                                  @NotNull List<UnknownSdkLookup> lookups,
                                                                  @NotNull TripleFunction<UnknownSdkLookup, UnknownSdk, ProgressIndicator, R> fun) {
    indicator.pushState();

    Map<UnknownSdk, R> result = new LinkedHashMap<>();
    for (Iterator<UnknownSdk> iterator = infos.iterator(); iterator.hasNext(); ) {
      UnknownSdk info = iterator.next();
      for (UnknownSdkLookup lookup : lookups) {

        indicator.pushState();
        R fix = fun.fun(lookup, info, indicator);
        indicator.popState();

        if (fix != null) {
          result.put(info, fix);
          iterator.remove();
          break;
        }
      }
    }

    indicator.popState();
    return result;
  }

  private static void registerNewSdkInJdkTable(@Nullable String sdkName, @NotNull Sdk sdk) {
    WriteAction.run(() -> {
      ProjectJdkTable table = ProjectJdkTable.getInstance();
      if (sdkName != null) {
        Sdk clash = table.findJdk(sdkName);
        if (clash != null) {
          LOG.warn("SDK with name " + sdkName + " already exists: clash=" + clash + ", new=" + sdk);
          return;
        }
        SdkModificator mod = sdk.getSdkModificator();
        mod.setName(sdkName);
        mod.commitChanges();
      }

      table.addJdk(sdk);
    });
  }
}
