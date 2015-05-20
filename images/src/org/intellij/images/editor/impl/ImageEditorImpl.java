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

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.*;
import org.intellij.images.editor.ImageDocument;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.ImageZoomModel;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.thumbnail.actionSystem.ThumbnailViewActions;
import org.intellij.images.vfs.IfsUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Image viewer implementation.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ImageEditorImpl extends VirtualFileAdapter implements ImageEditor {
    private final Project project;
    private final VirtualFile file;
    private final ImageEditorUI editorUI;
    private boolean disposed;

    ImageEditorImpl(@NotNull Project project, @NotNull VirtualFile file) {
        this.project = project;
        this.file = file;

        editorUI = new ImageEditorUI(this);

        VirtualFileManager.getInstance().addVirtualFileListener(this);

        setValue(file);
    }

    private void setValue(VirtualFile file) {
        try {
            editorUI.setImage(IfsUtil.getImage(file), IfsUtil.getFormat(file));
        }
        catch (Exception e) {
            //     Error loading image file
            editorUI.setImage(null, null);
        }
    }

    public boolean isValid() {
        ImageDocument document = editorUI.getImageComponent().getDocument();
        return document.getValue() != null;
    }

    public JComponent getComponent() {
        return editorUI;
    }

    public JComponent getContentComponent() {
        return editorUI.getImageComponent();
    }

    @NotNull
    public VirtualFile getFile() {
        return file;
    }

    @NotNull
    public Project getProject() {
        return project;
    }

    public ImageDocument getDocument() {
        return editorUI.getImageComponent().getDocument();
    }

    public void setTransparencyChessboardVisible(boolean visible) {
        editorUI.getImageComponent().setTransparencyChessboardVisible(visible);
        editorUI.repaint();
    }

    public boolean isTransparencyChessboardVisible() {
        return editorUI.getImageComponent().isTransparencyChessboardVisible();
    }

    public boolean isEnabledForActionPlace(String place) {
        // Disable for thumbnails action
        return !ThumbnailViewActions.ACTION_PLACE.equals(place);
    }

    public void setGridVisible(boolean visible) {
        editorUI.getImageComponent().setGridVisible(visible);
        editorUI.repaint();
    }

    public boolean isGridVisible() {
        return editorUI.getImageComponent().isGridVisible();
    }

    public boolean isDisposed() {
        return disposed;
    }

    public ImageZoomModel getZoomModel() {
        return editorUI.getZoomModel();
    }

    public void dispose() {
        editorUI.dispose();
        VirtualFileManager.getInstance().removeVirtualFileListener(this);
        disposed = true;
    }

    public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
        super.propertyChanged(event);
        if (file.equals(event.getFile())) {
            // Change document
            file.refresh(true, false, new Runnable() {
                public void run() {
                    if (ImageFileTypeManager.getInstance().isImage(file)) {
                        setValue(file);
                    } else {
                        setValue(null);
                        // Close editor
                        FileEditorManager editorManager = FileEditorManager.getInstance(project);
                        editorManager.closeFile(file);
                    }
                }
            });
        }
    }

    public void contentsChanged(@NotNull VirtualFileEvent event) {
        super.contentsChanged(event);
        if (file.equals(event.getFile())) {
            // Change document
            file.refresh(true, false, new Runnable() {
                public void run() {
                    setValue(file);
                }
            });
        }
    }
}
