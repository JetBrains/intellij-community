/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.intellij.images.completion;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.file.FileLookupInfoProvider;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.index.ImageInfoIndex;
import org.intellij.images.util.ImageInfo;
import org.jetbrains.annotations.NotNull;

public class ImageLookupInfoProvider extends FileLookupInfoProvider {
  @Override
  public Couple<String> getLookupInfo(@NotNull VirtualFile file, Project project) {
    ImageInfo imageInfo = ImageInfoIndex.getInfo(file, project);
    return imageInfo != null ? Couple.of(file.getName(), String.format("%sx%s", imageInfo.width, imageInfo.height)) : null;
  }

  @Override
  public FileType @NotNull [] getFileTypes() {
    return new FileType[]{ImageFileTypeManager.getInstance().getImageFileType()};
  }
}
