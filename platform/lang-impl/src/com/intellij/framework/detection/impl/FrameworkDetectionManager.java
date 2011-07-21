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
import com.intellij.framework.detection.FrameworkDetector;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.PlatformModifiableModelsProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.*;

/**
 * @author nik
 */
public class FrameworkDetectionManager extends AbstractProjectComponent implements FrameworkDetectionIndexListener,
                                                                                   TextEditorHighlightingPassFactory {
  private static final NotificationGroup FRAMEWORK_DETECTION_NOTIFICATION = NotificationGroup.balloonGroup("Framework Detection");
  private final Update myDetectionUpdate = new Update("detection") {
    @Override
    public void run() {
      runDetection();
    }
  };
  private final MultiMap<Integer, VirtualFile> myFilesToProcess = new MultiMap<Integer, VirtualFile>();
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
    myDetectionQueue = new MergingUpdateQueue("FrameworkDetectionQueue", 300, true, null, myProject);
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
  public void disposeComponent() {
    myDetectedFrameworksData.saveDetected();
  }

  @Override
  public void fileUpdated(@NotNull VirtualFile file, @NotNull Integer detectorId) {
    synchronized (myLock) {
      myFilesToProcess.putValue(detectorId, file);
    }
    myDetectionQueue.queue(myDetectionUpdate);
  }

  @Override
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    final Collection<Integer> detectors = FrameworkDetectorRegistry.getInstance().getDetectorsId(file.getFileType());
    if (!detectors.isEmpty()) {
      return new FrameworkDetectionHighlightingPass(editor, detectors);
    }
    return null;
  }

  private void runDetection() {
    Set<Integer> detectorsToProcess;
    synchronized (myLock) {
      detectorsToProcess = myDetectedFrameworksData.updateNewFiles(myFilesToProcess);
      myFilesToProcess.clear();
    }

    final FileBasedIndex index = FileBasedIndex.getInstance();
    Map<Integer, List<? extends DetectedFrameworkDescription>> newDescriptions = new HashMap<Integer, List<? extends DetectedFrameworkDescription>>();
    for (Integer key : detectorsToProcess) {
      Collection<VirtualFile> files = index.getContainingFiles(FrameworkDetectionIndex.NAME, key, GlobalSearchScope.projectScope(myProject));
      final Collection<VirtualFile> newFiles = myDetectedFrameworksData.retainNewFiles(key, files);
      FrameworkDetector detector = FrameworkDetectorRegistry.getInstance().getDetectorById(key);
      if (detector != null) {
        final List<? extends DetectedFrameworkDescription> frameworks = detector.detect(newFiles, new FrameworkDetectionContextImpl(myProject));
        final List<? extends DetectedFrameworkDescription> updated = myDetectedFrameworksData.updateFrameworksList(key, frameworks);
        if (!updated.isEmpty()) {
          newDescriptions.put(key, updated);
        }
      }
    }

    for (final Integer detectorId : newDescriptions.keySet()) {
      for (final DetectedFrameworkDescription description : newDescriptions.get(detectorId)) {
        final String text = ProjectBundle.message("framework.detected.info.text", description.getDescription());
        FRAMEWORK_DETECTION_NOTIFICATION.createNotification("Frameworks detected", text, NotificationType.INFORMATION, new NotificationListener() {
          @Override
          public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
              setupFramework(description, detectorId, notification);
            }
          }
        }).notify(myProject);
      }
    }
  }

  private void setupFramework(DetectedFrameworkDescription description, Integer detectorId, Notification notification) {
    notification.expire();
    AccessToken token = WriteAction.start();
    try {
      description.configureFramework(new PlatformModifiableModelsProvider());
      final Collection<? extends VirtualFile> files = description.getRelatedFiles();
      myDetectedFrameworksData.putExistentFrameworkFiles(detectorId, files);
    }
    finally {
      token.finish();
    }
  }

  private class FrameworkDetectionHighlightingPass extends TextEditorHighlightingPass {
    private final Collection<Integer> myDetectors;

    public FrameworkDetectionHighlightingPass(Editor editor, Collection<Integer> detectors) {
      super(FrameworkDetectionManager.this.myProject, editor.getDocument(), false);
      myDetectors = detectors;
    }

    @Override
    public void doCollectInformation(ProgressIndicator progress) {
      for (Integer detectorId : myDetectors) {
        FileBasedIndex.getInstance().getValues(FrameworkDetectionIndex.NAME, detectorId, GlobalSearchScope.projectScope(myProject));
      }
    }

    @Override
    public void doApplyInformationToEditor() {
    }
  }
}
