/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.framework.detection.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.framework.detection.DetectionExcludesConfiguration;
import com.intellij.framework.detection.FrameworkDetector;
import com.intellij.framework.detection.impl.exclude.DetectionExcludesConfigurationImpl;
import com.intellij.framework.detection.impl.ui.ConfigureDetectedFrameworksDialog;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.PlatformModifiableModelsProvider;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.startup.StartupManager;
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

import javax.swing.event.HyperlinkEvent;
import java.util.*;

/**
 * @author nik
 */
public class FrameworkDetectionManager extends AbstractProjectComponent implements FrameworkDetectionIndexListener,
                                                                                   TextEditorHighlightingPassFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.framework.detection.impl.FrameworkDetectionManager");
  private static final NotificationGroup FRAMEWORK_DETECTION_NOTIFICATION = NotificationGroup.balloonGroup("Framework Detection");
  private final Update myDetectionUpdate = new Update("detection") {
    @Override
    public void run() {
      doRunDetection();
    }
  };
  private final Set<Integer> myDetectorsToProcess = new HashSet<Integer>();
  private MergingUpdateQueue myDetectionQueue;
  private final Object myLock = new Object();
  private DetectedFrameworksData myDetectedFrameworksData;

  public static FrameworkDetectionManager getInstance(@NotNull Project project) {
    return project.getComponent(FrameworkDetectionManager.class);
  }

  public FrameworkDetectionManager(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, TextEditorHighlightingPassRegistrar.Anchor.LAST, -1, false, false);
  }

  @Override
  public void initComponent() {
    if (!myProject.isDefault() && !ApplicationManager.getApplication().isUnitTestMode()) {
      doInitialize();
    }
  }

  public void doInitialize() {
    myDetectionQueue = new MergingUpdateQueue("FrameworkDetectionQueue", 500, true, null, myProject);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myDetectionQueue.setPassThrough(false);
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
  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      @Override
      public void run() {
        final Collection<Integer> ids = FrameworkDetectorRegistry.getInstance().getAllDetectorIds();
        synchronized (myLock) {
          myDetectorsToProcess.clear();
          myDetectorsToProcess.addAll(ids);
        }
        queueDetection();
      }
    });
  }

  @Override
  public void disposeComponent() {
    doDispose();
  }

  public void doDispose() {
    if (myDetectedFrameworksData != null) {
      myDetectedFrameworksData.saveDetected();
      myDetectedFrameworksData = null;
    }
  }

  @Override
  public void fileUpdated(@NotNull VirtualFile file, @NotNull Integer detectorId) {
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

  @Override
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    final Collection<Integer> detectors = FrameworkDetectorRegistry.getInstance().getDetectorIds(file.getFileType());
    if (!detectors.isEmpty()) {
      return new FrameworkDetectionHighlightingPass(editor, detectors);
    }
    return null;
  }

  private void doRunDetection() {
    Set<Integer> detectorsToProcess;
    synchronized (myLock) {
      detectorsToProcess = new HashSet<Integer>(myDetectorsToProcess);
      detectorsToProcess.addAll(myDetectorsToProcess);
      myDetectorsToProcess.clear();
    }
    if (detectorsToProcess.isEmpty()) return;

    if (LOG.isDebugEnabled()) {
      LOG.debug("Starting framework detectors: " + detectorsToProcess);
    }
    final FileBasedIndex index = FileBasedIndex.getInstance();
    List<DetectedFrameworkDescription> newDescriptions = new ArrayList<DetectedFrameworkDescription>();
    List<DetectedFrameworkDescription> oldDescriptions = new ArrayList<DetectedFrameworkDescription>();
    final DetectionExcludesConfiguration excludesConfiguration = DetectionExcludesConfiguration.getInstance(myProject);
    for (Integer id : detectorsToProcess) {
      final List<? extends DetectedFrameworkDescription> frameworks = runDetector(id, index, excludesConfiguration, true);
      oldDescriptions.addAll(frameworks);
      final Collection<? extends DetectedFrameworkDescription> updated = myDetectedFrameworksData.updateFrameworksList(id, frameworks);
      newDescriptions.addAll(updated);
      oldDescriptions.removeAll(updated);
      if (LOG.isDebugEnabled()) {
        LOG.debug(frameworks.size() + " frameworks detected, " + updated.size() + " changed");
      }
    }

    Set<String> frameworkNames = new HashSet<String>();
    for (final DetectedFrameworkDescription description : FrameworkDetectionUtil.removeDisabled(newDescriptions, oldDescriptions)) {
      frameworkNames.add(description.getDetector().getFrameworkType().getPresentableName());
    }
    if (!frameworkNames.isEmpty()) {
      String names = StringUtil.join(frameworkNames, ", ");
      final String text = ProjectBundle.message("framework.detected.info.text", names, frameworkNames.size());
      FRAMEWORK_DETECTION_NOTIFICATION
        .createNotification("Frameworks detected", text, NotificationType.INFORMATION, new NotificationListener() {
          @Override
          public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
              showSetupFrameworksDialog(notification);
            }
          }
        }).notify(myProject);
    }
  }

  private List<? extends DetectedFrameworkDescription> runDetector(Integer detectorId,
                                                                   FileBasedIndex index,
                                                                   DetectionExcludesConfiguration excludesConfiguration,
                                                                   final boolean processNewFilesOnly) {
    Collection<VirtualFile> acceptedFiles = index.getContainingFiles(FrameworkDetectionIndex.NAME, detectorId, GlobalSearchScope.projectScope(myProject));
    final Collection<VirtualFile> filesToProcess;
    if (processNewFilesOnly) {
      filesToProcess = myDetectedFrameworksData.retainNewFiles(detectorId, acceptedFiles);
    }
    else {
      filesToProcess = new ArrayList<VirtualFile>(acceptedFiles);
    }
    FrameworkDetector detector = FrameworkDetectorRegistry.getInstance().getDetectorById(detectorId);
    if (detector == null) {
      LOG.info("Framework detector not found by id " + detectorId);
      return Collections.emptyList();
    }

    ((DetectionExcludesConfigurationImpl)excludesConfiguration).removeExcluded(filesToProcess, detector.getFrameworkType());
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
      DumbService.getInstance(myProject).showDumbModeNotification("Information about detected frameworks is not available until indices are built");
      return;
    }

    if (descriptions.isEmpty()) {
      Messages.showInfoMessage(myProject, "No frameworks are detected", "Framework Detection");
      return;
    }
    final ConfigureDetectedFrameworksDialog dialog = new ConfigureDetectedFrameworksDialog(myProject, descriptions);
    dialog.show();
    if (dialog.isOK()) {
      notification.expire();
      List<DetectedFrameworkDescription> selected = dialog.getSelectedFrameworks();
      FrameworkDetectionUtil.setupFrameworks(selected, new PlatformModifiableModelsProvider(), new DefaultModulesProvider(myProject));
      for (DetectedFrameworkDescription description : selected) {
        final int detectorId = FrameworkDetectorRegistry.getInstance().getDetectorId(description.getDetector());
        myDetectedFrameworksData.putExistentFrameworkFiles(detectorId, description.getRelatedFiles());
      }
    }
  }

  private List<? extends DetectedFrameworkDescription> getValidDetectedFrameworks() {
    final Set<Integer> detectors = myDetectedFrameworksData.getDetectorsForDetectedFrameworks();
    List<DetectedFrameworkDescription> descriptions = new ArrayList<DetectedFrameworkDescription>();
    final FileBasedIndex index = FileBasedIndex.getInstance();
    final DetectionExcludesConfiguration excludesConfiguration = DetectionExcludesConfiguration.getInstance(myProject);
    for (Integer id : detectors) {
      final Collection<? extends DetectedFrameworkDescription> frameworks = runDetector(id, index, excludesConfiguration, false);
      for (DetectedFrameworkDescription framework : frameworks) {
        descriptions.add(framework);
      }
    }
    return FrameworkDetectionUtil.removeDisabled(descriptions);
  }

  @TestOnly
  public void runDetection() {
    ensureIndexIsUpToDate(FrameworkDetectorRegistry.getInstance().getAllDetectorIds());
    doRunDetection();
  }

  @TestOnly
  public List<? extends DetectedFrameworkDescription> getDetectedFrameworks() {
    return getValidDetectedFrameworks();
  }

  private void ensureIndexIsUpToDate(final Collection<Integer> detectors) {
    for (Integer detectorId : detectors) {
      FileBasedIndex.getInstance().getValues(FrameworkDetectionIndex.NAME, detectorId, GlobalSearchScope.projectScope(myProject));
    }
  }

  private class FrameworkDetectionHighlightingPass extends TextEditorHighlightingPass {
    private final Collection<Integer> myDetectors;

    public FrameworkDetectionHighlightingPass(Editor editor, Collection<Integer> detectors) {
      super(FrameworkDetectionManager.this.myProject, editor.getDocument(), false);
      myDetectors = detectors;
    }

    @Override
    public void doCollectInformation(@NotNull ProgressIndicator progress) {
      ensureIndexIsUpToDate(myDetectors);
    }

    @Override
    public void doApplyInformationToEditor() {
    }
  }
}
