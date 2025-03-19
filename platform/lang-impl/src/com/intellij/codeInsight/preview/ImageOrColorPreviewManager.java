// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.preview;

import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

final class ImageOrColorPreviewManager implements EditorMouseMotionListener, EditorFactoryListener {

  @Override
  public void editorCreated(@NotNull EditorFactoryEvent event) {
    Project project = event.getEditor().getProject();
    if (project != null) {
      project.getService(ImageOrColorPreviewService.class).attach(event.getEditor());
    }
  }

  @Override
  public void editorReleased(@NotNull EditorFactoryEvent event) {
    Project project = event.getEditor().getProject();
    if (project != null) {
      ImageOrColorPreviewService service = project.getServiceIfCreated(ImageOrColorPreviewService.class);
      if (service != null) {
        service.detach(event.getEditor());
      }
    }
  }

}
