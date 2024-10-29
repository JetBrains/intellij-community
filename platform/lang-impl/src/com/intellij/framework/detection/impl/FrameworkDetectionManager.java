// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.detection.impl;

import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.framework.detection.FrameworkDetector;
import com.intellij.framework.detection.impl.ui.ConfigureDetectedFrameworksDialog;
import com.intellij.ide.IdeBundle;
import com.intellij.lang.LangBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.*;
import com.intellij.openapi.roots.PlatformModifiableModelsProvider;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
public final class FrameworkDetectionManager implements FrameworkDetectionIndexListener, Disposable {
  private static final Logger LOG = Logger.getInstance(FrameworkDetectionManager.class);

  private final Project myProject;

  private final Object myLock = new Object();
  private final Set<String> myDetectorsToProcess = new HashSet<>();

  private final FrameworkDetectorQueue myDetectionQueue;
  private DetectedFrameworksData myDetectedFrameworksData;

  public static FrameworkDetectionManager getInstance(@NotNull Project project) {
    return project.getService(FrameworkDetectionManager.class);
  }

  public FrameworkDetectionManager(@NotNull Project project,
                                   @NotNull CoroutineScope coroutineScope) {
    myProject = project;
    myDetectionQueue = new FrameworkDetectorQueue(project, coroutineScope);
    myDetectionQueue.setNotificationListener(this::notifyUser);

    if (!myProject.isDefault() && !ApplicationManager.getApplication().isUnitTestMode()) {
      doInitialize();
    }

    FrameworkDetector.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull FrameworkDetector extension, @NotNull PluginDescriptor pluginDescriptor) {
        synchronized (myLock) {
          myDetectorsToProcess.add(extension.getDetectorId());
        }
        queueDetection();
      }

      @Override
      public void extensionRemoved(@NotNull FrameworkDetector extension,
                                   @NotNull PluginDescriptor pluginDescriptor) {
        synchronized (myLock) {
          myDetectorsToProcess.remove(extension.getDetectorId());
        }
        DetectedFrameworksData frameworksData = myDetectedFrameworksData;
        if (frameworksData != null) {
          frameworksData.updateFrameworksList(extension.getDetectorId(), Collections.emptyList());
        }
      }
    }, project);
  }

  void jpsProjectLoaded(@NotNull FrameworkDetectorRegistry frameworkDetectorRegistry) {
    LOG.debug("Queue frameworks detection after opening the project");
    Collection<String> ids = frameworkDetectorRegistry.getAllDetectorIds();
    synchronized (myLock) {
      myDetectorsToProcess.clear();
      myDetectorsToProcess.addAll(ids);
    }
    queueDetection();
  }

  public void doInitialize() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myDetectionQueue.suspend();
    }

    myDetectedFrameworksData = new DetectedFrameworksData(myProject);
    myDetectionQueue.setDetectedFrameworksData(myDetectedFrameworksData);

    FrameworkDetectionIndex.getInstance().addListener(this, myProject);
    myProject.getMessageBus().connect(this).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void enteredDumbMode() {
        myDetectionQueue.suspend();
      }

      @Override
      public void exitDumbMode() {
        myDetectionQueue.resume(myDetectorsToProcess);
      }
    });
  }

  @Override
  public void dispose() {
    doDispose();
  }

  public void doDispose() {
    if (myDetectedFrameworksData != null) {
      myDetectedFrameworksData.saveDetected();
      myDetectedFrameworksData = null;
    }
  }

  @Override
  public void fileUpdated(@NotNull VirtualFile file, @NotNull String detectorId) {
    synchronized (myLock) {
      myDetectorsToProcess.add(detectorId);
    }
    queueDetection();
  }

  private void queueDetection() {
    synchronized (myLock) {
      myDetectionQueue.queueDetection(myDetectorsToProcess);
    }
  }

  private void notifyUser(Collection<String> frameworkNames) {
    synchronized (myLock) {
      myDetectorsToProcess.clear();
    }

    String names = StringUtil.join(frameworkNames, ", ");
    String text = ProjectBundle.message("framework.detected.info.text", names, frameworkNames.size());
    NotificationGroupManager.getInstance().getNotificationGroup("Framework Detection")
      .createNotification(ProjectBundle.message("notification.title.frameworks.detected"), text, NotificationType.INFORMATION)
      .addAction(new NotificationAction(IdeBundle.messagePointer("action.Anonymous.text.configure")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          showSetupFrameworksDialog(notification);
        }
      })
      .notify(myProject);
  }

  private void showSetupFrameworksDialog(Notification notification) {
    List<? extends DetectedFrameworkDescription> descriptions;
    try {
      descriptions = getValidDetectedFrameworks();
    }
    catch (IndexNotReadyException e) {
      DumbService.getInstance(myProject)
        .showDumbModeNotificationForFunctionality(
          LangBundle.message("popup.content.information.about.detected.frameworks"),
          DumbModeBlockedFunctionality.FrameworkDetection);
      return;
    }

    if (descriptions.isEmpty()) {
      Messages.showInfoMessage(myProject, LangBundle.message("dialog.message.no.frameworks.are.detected"),
                               LangBundle.message("dialog.title.framework.detection"));
      return;
    }
    final ConfigureDetectedFrameworksDialog dialog = new ConfigureDetectedFrameworksDialog(myProject, descriptions);
    if (dialog.showAndGet()) {
      notification.expire();
      List<DetectedFrameworkDescription> selected = dialog.getSelectedFrameworks();
      FrameworkDetectionUtil.setupFrameworks(selected, new PlatformModifiableModelsProvider(), new DefaultModulesProvider(myProject));
      for (DetectedFrameworkDescription description : selected) {
        final @NotNull String detectorId = description.getDetector().getDetectorId();
        myDetectedFrameworksData.putExistentFrameworkFiles(detectorId, description.getRelatedFiles());
      }
    }
  }

  private List<? extends DetectedFrameworkDescription> getValidDetectedFrameworks() {
    Set<String> detectors = myDetectedFrameworksData.getDetectorsForDetectedFrameworks();
    List<DetectedFrameworkDescription> descriptions = new ArrayList<>();
    for (String id : detectors) {
      Collection<? extends DetectedFrameworkDescription> frameworks = myDetectionQueue.runDetector(id, false);
      descriptions.addAll(frameworks);
    }
    return FrameworkDetectionUtil.removeDisabled(descriptions);
  }

  @TestOnly
  public void runDetection() {
    ensureIndexIsUpToDate(myProject, FrameworkDetectorRegistry.getInstance().getAllDetectorIds());
    myDetectionQueue.testRunDetection(myDetectorsToProcess);
  }

  @TestOnly
  public List<? extends DetectedFrameworkDescription> getDetectedFrameworks() {
    return getValidDetectedFrameworks();
  }

  private static void ensureIndexIsUpToDate(@NotNull Project project, Collection<String> detectors) {
    for (String detectorId : detectors) {
      FileBasedIndex.getInstance().getValues(FrameworkDetectionIndex.NAME, detectorId, GlobalSearchScope.projectScope(project));
    }
  }
}
