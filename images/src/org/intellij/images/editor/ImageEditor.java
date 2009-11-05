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
package org.intellij.images.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import org.intellij.images.ui.ImageComponentDecorator;

import javax.swing.*;

/**
 * Image viewer.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface ImageEditor extends Disposable, VirtualFileListener, ImageComponentDecorator {
    VirtualFile getFile();

    Project getProject();

    ImageDocument getDocument();

    JComponent getComponent();

    /**
     * Return the target of image editing area within entire component,
     * returned by {@link #getComponent()}.
     *
     * @return Content component
     */
    JComponent getContentComponent();

    /**
     * Return <code>true</code> if editor show valid image.
     *
     * @return <code>true</code> if editor show valid image.
     */
    boolean isValid();

    /**
     * Return <code>true</code> if editor is already disposed.
     *
     * @return <code>true</code> if editor is already disposed.
     */
    boolean isDisposed();

    ImageZoomModel getZoomModel();

    void setGridVisible(boolean visible);

    boolean isGridVisible();
}
