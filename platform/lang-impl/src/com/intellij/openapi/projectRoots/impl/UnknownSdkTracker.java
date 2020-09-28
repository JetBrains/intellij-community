// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.*;
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
    if (!isEnabled() || !Registry.is("unknown.sdk.modal")) {
      showStatus.showEmptyStatus();
      return;
    }

    ProgressManager.getInstance()
      .run(new Task.Modal(myProject, ProjectBundle.message("progress.title.resolving.sdks"), true) {
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
        if (!isEnabled() || !Registry.is("unknown.sdk.auto")) {
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

               List<UnknownSdkFix> fixProposals = new ArrayList<>();

               fixable.removeAll(invalidSdks);
               for (UnknownInvalidSdk invalidSdk : invalidSdks) {
                 var localSdkFix = localFixes.remove(invalidSdk);
                 var downloadableSdkFix = downloadFixes.remove(invalidSdk);
                 fixProposals.add(invalidSdk.buildFix(myProject, localSdkFix, downloadableSdkFix));
               }

               var allMissingSdks = ImmutableSet.<UnknownSdk>builder()
                 .addAll(downloadFixes.keySet())
                 .addAll(localFixes.keySet())
                 .addAll(fixable)
                 .build();

               for (UnknownSdk unknownSdk : allMissingSdks) {
                 String name = unknownSdk.getSdkName();
                 if (name == null) continue;

                 var downloadFix = downloadFixes.get(unknownSdk);
                 var localSdkFix = localFixes.get(unknownSdk);

                 UnknownSdkFixAction theFixAction;
                 if (downloadFix != null) theFixAction = new UnknownMissingSdkFixDownload(myProject, unknownSdk, downloadFix);
                 else if (localSdkFix != null)  theFixAction = new UnknownMissingSdkFixLocal(name, unknownSdk, localSdkFix);
                 else theFixAction = null;

                 fixProposals.add(new UnknownMissingSdkFix(myProject,
                                                           name,
                                                           unknownSdk.getSdkType(),
                                                           theFixAction));
               }

               showStatus.showStatus(fixProposals, indicator);
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
    void showStatus(@NotNull List<UnknownSdkFix> fixes, @NotNull ProgressIndicator indicator);

    default void showInterruptedStatus() {
      showStatus(Collections.emptyList(), new EmptyProgressIndicator());
    }

    default void showEmptyStatus() {
      showStatus(Collections.emptyList(), new EmptyProgressIndicator());
    }
  }

  private class DefaultShowStatusCallbackAdapter implements ShowStatusCallback {
    @Override
    public void showStatus(@NotNull List<UnknownSdkFix> fixes, @NotNull ProgressIndicator indicator) {
      fixes = applyAutoFixesAndNotify(fixes, indicator);
      UnknownSdkEditorNotification.getInstance(myProject).showNotifications(fixes);
    }
  }

  @NotNull
  public List<UnknownSdkFix> applyAutoFixesAndNotify(@NotNull List<UnknownSdkFix> fixes, @NotNull ProgressIndicator indicator) {
    List<UnknownSdkFix> otherFixes = new ArrayList<>();
    List<UnknownMissingSdkFixLocal> localFixes = new ArrayList<>();

    for (UnknownSdkFix fix : fixes) {
      var action = fix.getSuggestedFixAction();
      if (action instanceof UnknownMissingSdkFixLocal) {
        localFixes.add((UnknownMissingSdkFixLocal)action);
      } else {
        otherFixes.add(fix);
      }
    }

    if (!localFixes.isEmpty()) {
      indicator.pushState();
      indicator.setText(ProjectBundle.message("progress.text.configuring.sdks"));

      for (UnknownMissingSdkFixLocal fix : new ArrayList<>(localFixes)) {
        try {
          fix.applySuggestionModal(indicator);
        }
        catch (Throwable t) {
          LOG.warn("Failed to apply SDK fix: " + fix + ". " + t.getMessage(), t);
          localFixes.remove(fix);
        }
      }

      UnknownSdkBalloonNotification.getInstance(myProject).notifyFixedSdks(localFixes);
      indicator.popState();
    }
    return otherFixes;
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

  @ApiStatus.Internal
  public static UnknownSdkDownloadTask createDownloadFixTask(@NotNull UnknownSdk info,
                                                             @NotNull UnknownSdkDownloadableSdkFix fix,
                                                             @NotNull Consumer<? super Sdk> onSdkNameReady,
                                                             @NotNull Consumer<? super Sdk> onCompleted) {
    return new UnknownSdkDownloadTask(info, fix,
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

  @ApiStatus.Internal
  public static void downloadFix(@Nullable Project project,
                                 @NotNull UnknownSdk info,
                                 @NotNull UnknownSdkDownloadableSdkFix fix,
                                 @NotNull Consumer<? super Sdk> onSdkNameReady,
                                 @NotNull Consumer<? super Sdk> onCompleted) {
    createDownloadFixTask(info, fix, onSdkNameReady, onCompleted).runAsync(project);
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

  @ApiStatus.Internal
  public static void configureLocalSdk(@NotNull UnknownSdk info,
                                       @NotNull UnknownSdkLocalSdkFix fix,
                                       @NotNull Consumer<? super Sdk> onCompleted) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (!Registry.is("unknown.sdk.apply.local.fix")) {
        onCompleted.consume(null);
        return;
      }

      try {
        Sdk sdk = applyLocalFix(info, fix);
        onCompleted.consume(sdk);
      } catch (Exception error) {
        LOG.warn("Failed to configure " + info.getSdkType().getPresentableName() + " " + " for " + info + " for path " + fix + ". " + error.getMessage(), error);
        onCompleted.consume(null);
      }
    });
  }

  @Nullable
  static Sdk applyLocalFix(@NotNull UnknownSdk info, @NotNull UnknownSdkLocalSdkFix fix) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!Registry.is("unknown.sdk.apply.local.fix")) return null;

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
    return sdk;
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
