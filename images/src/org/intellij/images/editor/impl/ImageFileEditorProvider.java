/*
 * Copyright 2004-2005 Alexey Efimov
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
package org.intellij.images.editor.impl;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.util.Alarm;
import org.intellij.images.editor.impl.jcef.JCefImageViewer;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.vfs.IfsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Image editor provider.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ImageFileEditorProvider implements FileEditorProvider, DumbAware {
  @NonNls private static final String EDITOR_TYPE_ID = "images";

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return ImageFileTypeManager.getInstance().isImage(file);
  }

  @Override
  public boolean acceptRequiresReadAction() {
    return false;
  }

  @Override
  @NotNull
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    if (IfsUtil.isSVG(file)) {
      TextEditor editor = (TextEditor)TextEditorProvider.getInstance().createEditor(project, file);
      if (JBCefApp.isSupported() && RegistryManager.getInstance().is("ide.browser.jcef.svg-viewer.enabled")) {
        return new TextEditorWithPreview(editor, new JCefImageViewer(file, "image/svg+xml"), "SvgEditor");
      }
      else {
        ImageFileEditorImpl viewer = new ImageFileEditorImpl(project, file);
        editor.getEditor().getDocument().addDocumentListener(new DocumentListener() {
          final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, editor);

          @Override
          public void documentChanged(@NotNull DocumentEvent event) {
            myAlarm.cancelAllRequests();
            myAlarm.addRequest(
              () -> ((ImageEditorImpl)viewer.getImageEditor())
                .setValue(new LightVirtualFile("preview.svg", file.getFileType(), event.getDocument().getText())), 500);
          }
        }, editor);
        return new TextEditorWithPreview(editor, viewer, "SvgEditor");
      }
    }
    return new ImageFileEditorImpl(project, file);
  }

  @Override
  @NotNull
  public String getEditorTypeId() {
    return EDITOR_TYPE_ID;
  }

  @Override
  @NotNull
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }
}
