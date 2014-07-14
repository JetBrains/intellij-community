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
import com.intellij.util.indexing.FileBasedIndex;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.index.ImageInfoIndex;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class ImageLookupInfoProvider extends FileLookupInfoProvider {

  public Couple<String> getLookupInfo(@NotNull VirtualFile file, Project project) {
    final String[] s = new String[] {null};
    ImageInfoIndex.processValues(file, new FileBasedIndex.ValueProcessor<ImageInfoIndex.ImageInfo>() {
      @SuppressWarnings({"HardCodedStringLiteral"})
      public boolean process(VirtualFile file, ImageInfoIndex.ImageInfo value) {
        s[0] = String.format("%sx%s", value.width, value.height);
        return true;
      }
    }, project);

    return s[0] == null ? null : Couple.of(file.getName(), s[0]);
  }

  @NotNull
  @Override
  public FileType[] getFileTypes() {
    return new FileType[]{ImageFileTypeManager.getInstance().getImageFileType()};
  }
}
