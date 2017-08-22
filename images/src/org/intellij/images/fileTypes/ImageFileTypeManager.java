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
package org.intellij.images.fileTypes;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * File type manager.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public abstract class ImageFileTypeManager extends FileTypeFactory {
    public static ImageFileTypeManager getInstance() {
        return ServiceManager.getService(ImageFileTypeManager.class);
    }

    /**
     * Check that file is image.
     *
     * @param file File to check
     * @return Return {@code true} if image file is file with Images file type
     */
    public abstract boolean isImage(VirtualFile file);

    public abstract FileType getImageFileType();
}
