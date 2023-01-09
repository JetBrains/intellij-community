// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.detection.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.framework.detection.DetectionExcludesConfiguration;
import com.intellij.framework.detection.FrameworkDetector;
import com.intellij.framework.detection.impl.exclude.DetectionExcludesConfigurationImpl;
import com.intellij.framework.detection.impl.ui.ConfigureDetectedFrameworksDialog;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.lang.LangBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.PlatformModifiableModelsProvider;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

@Service(Service.Level.PROJECT)
public final class FrameworkDetectionManager implements FrameworkDetectionIndexListener, Disposable {
  private static final Logger LOG = Logger.getInstance(FrameworkDetectionManager.class);

  private final Update myDetectionUpdate = new Update("detection") {
    @Override
    public void run() {
      doRunDetection();
    }
  };

  private final Set<String> myDetectorsToProcess = new HashSet<>();
  private final Project myProject;
  private MergingUpdateQueue myDetectionQueue;
  private final Object myLock = new Object();
  private DetectedFrameworksData myDetectedFrameworksData;

  public static FrameworkDetectionManager getInstance(@NotNull Project project) {
    return project.getService(FrameworkDetectionManager.class);
  }

  public FrameworkDetectionManager(@NotNull Project project) {
    myProject = project;

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

  private void projectOpened() {
    @NotNull Collection<String> ids = FrameworkDetectorRegistry.getInstance().getAllDetectorIds();
    synchronized (myLock) {
      myDetectorsToProcess.clear();
      myDetectorsToProcess.addAll(ids);
    }
    queueDetection();
  }

  static final class MyPostStartupActivity implements StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
      getInstance(project).projectOpened();
    }
  }

  static final class FrameworkDetectionHighlightingPassFactory implements TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
    @Override
    public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
      registrar.registerTextEditorHighlightingPass(this, TextEditorHighlightingPassRegistrar.Anchor.LAST, -1, false, false);
    }

    @Override
    public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
      final @NotNull Collection<String> detectors = FrameworkDetectorRegistry.getInstance().getDetectorIds(file.getFileType());
      if (!detectors.isEmpty()) {
        return new FrameworkDetectionHighlightingPass(file.getProject(), editor, detectors);
      }
      return null;
    }
  }

  public void doInitialize() {
    myDetectionQueue = new MergingUpdateQueue("FrameworkDetectionQueue", 500, true, null, myProject, null, false);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myDetectionQueue.hideNotify();
    }
    myDetectedFrameworksData = new DetectedFrameworksData(myProject);
    FrameworkDetectionIndex.getInstance().addListener(this, myProject);
    myProject.getMessageBus().connect().subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void enteredDumbMode() {
        myDetectionQueue.suspend();
      }

      @Override
      public void exitDumbMode() {
        myDetectionQueue.resume();
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
    if (myDetectionQueue != null) {
      myDetectionQueue.queue(myDetectionUpdate);
    }
  }

  private void doRunDetection() {
    if (LightEdit.owns(myProject)) {
      return;
    }
    Set<String> detectorsToProcess;
    synchronized (myLock) {
      detectorsToProcess = new HashSet<>(myDetectorsToProcess);
      myDetectorsToProcess.clear();
    }
    if (detectorsToProcess.isEmpty()) {
      return;
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Starting framework detectors: " + detectorsToProcess);
    }
    List<DetectedFrameworkDescription> newDescriptions = new ArrayList<>();
    List<DetectedFrameworkDescription> oldDescriptions = new ArrayList<>();
    for (String id : detectorsToProcess) {
      final List<? extends DetectedFrameworkDescription> frameworks = ReadAction
        .nonBlocking(() -> runDetector(id, true))
        .inSmartMode(myProject)
        .executeSynchronously();
      oldDescriptions.addAll(frameworks);
      final Collection<? extends DetectedFrameworkDescription> updated = myDetectedFrameworksData.updateFrameworksList(id, frameworks);
      newDescriptions.addAll(updated);
      oldDescriptions.removeAll(updated);
      if (LOG.isDebugEnabled()) {
        LOG.debug(frameworks.size() + " frameworks detected, " + updated.size() + " changed");
      }
    }

    if (!newDescriptions.isEmpty()) {
      Set<String> frameworkNames =
      ReadAction.nonBlocking(() -> {
        Set<String> names = new HashSet<>();
        for (final DetectedFrameworkDescription description : FrameworkDetectionUtil.removeDisabled(newDescriptions, oldDescriptions)) {
          names.add(description.getDetector().getFrameworkType().getPresentableName());
        }
        return names;
      }).executeSynchronously();
      if (!frameworkNames.isEmpty()) {
        String names = StringUtil.join(frameworkNames, ", ");
        final String text = ProjectBundle.message("framework.detected.info.text", names, frameworkNames.size());
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
    }
  }

  private List<? extends DetectedFrameworkDescription> runDetector(String detectorId, final boolean processNewFilesOnly) {
    Collection<VirtualFile> acceptedFiles = FileBasedIndex.getInstance().getContainingFiles(FrameworkDetectionIndex.NAME, detectorId,
                                                                                            GlobalSearchScope.projectScope(myProject));
    final Collection<VirtualFile> filesToProcess;
    if (processNewFilesOnly) {
      filesToProcess = myDetectedFrameworksData.retainNewFiles(detectorId, acceptedFiles);
    }
    else {
      filesToProcess = new ArrayList<>(acceptedFiles);
    }
    FrameworkDetector detector = FrameworkDetectorRegistry.getInstance().getDetectorById(detectorId);
    if (detector == null) {
      LOG.info("Framework detector not found by id " + detectorId);
      return Collections.emptyList();
    }

    DetectionExcludesConfigurationImpl excludesConfiguration = (DetectionExcludesConfigurationImpl)DetectionExcludesConfiguration.getInstance(myProject);
    excludesConfiguration.removeExcluded(filesToProcess, detector.getFrameworkType());
    if (LOG.isDebugEnabled()) {
      LOG.debug("Detector '" + detector.getDetectorId() + "': " + acceptedFiles.size() + " accepted files, " + filesToProcess.size() + " files to process");
    }
    final List<? extends DetectedFrameworkDescription> frameworks;
    if (!filesToProcess.isEmpty()) {
      frameworks = detector.detect(filesToProcess, new FrameworkDetectionContextImpl(myProject));
    }
    else {
      frameworks = Collections.emptyList();
    }
    return frameworks;
  }

  private void showSetupFrameworksDialog(Notification notification) {
    List<? extends DetectedFrameworkDescription> descriptions;
    try {
      descriptions = getValidDetectedFrameworks();
    }
    catch (IndexNotReadyException e) {
      DumbService.getInstance(myProject)
        .showDumbModeNotification(
          LangBundle.message("popup.content.information.about.detected.frameworks"));
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
    final Set<String> detectors = myDetectedFrameworksData.getDetectorsForDetectedFrameworks();
    List<DetectedFrameworkDescription> descriptions = new ArrayList<>();
    for (String id : detectors) {
      final Collection<? extends DetectedFrameworkDescription> frameworks = runDetector(id, false);
      descriptions.addAll(frameworks);
    }
    return FrameworkDetectionUtil.removeDisabled(descriptions);
  }

  @TestOnly
  public void runDetection() {
    ensureIndexIsUpToDate(myProject, FrameworkDetectorRegistry.getInstance().getAllDetectorIds());
    doRunDetection();
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

  private static final class FrameworkDetectionHighlightingPass extends TextEditorHighlightingPass {
    private final Collection<String> myDetectors;

    FrameworkDetectionHighlightingPass(@NotNull Project project, Editor editor, Collection<String> detectors) {
      super(project, editor.getDocument(), false);

      myDetectors = detectors;
    }

    @Override
    public void doCollectInformation(@NotNull ProgressIndicator progress) {
      ensureIndexIsUpToDate(myProject, myDetectors);
    }

    @Override
    public void doApplyInformationToEditor() {
    }
  }
}
