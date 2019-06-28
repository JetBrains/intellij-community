// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.fileTypes;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import org.intellij.images.index.ImageInfoIndex;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author spleaner
 */
public class ImageDocumentationProvider extends AbstractDocumentationProvider {
  private static final int MAX_IMAGE_SIZE = 300;

  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    final Ref<String> result = Ref.create();

    if (element instanceof PsiFileSystemItem && !((PsiFileSystemItem)element).isDirectory()) {
      final VirtualFile file = ((PsiFileSystemItem)element).getVirtualFile();
      if (file instanceof VirtualFileWithId && !DumbService.isDumb(element.getProject())) {
        ImageInfoIndex.processValues(file, (file1, value) -> {
          int imageWidth = value.width;
          int imageHeight = value.height;

          int maxSize = Math.max(value.width, value.height);
          if (maxSize > MAX_IMAGE_SIZE) {
            double scaleFactor = (double)MAX_IMAGE_SIZE / (double)maxSize;
            imageWidth *= scaleFactor;
            imageHeight *= scaleFactor;
          }
          try {
            String path = file1.getPath();
            if (SystemInfo.isWindows) {
              path = "/" + path;
            }
            final String url = new URI("file", null, path, null).toString();
            result.set(String.format("<img src=\"%s\" width=\"%s\" height=\"%s\"><p>%sx%s, %sbpp</p>", url, imageWidth,
                                 imageHeight, value.width, value.height, value.bpp));
          }
          catch (URISyntaxException ignored) {
            // nothing
          }
          return true;
        }, element.getProject());
      }
    }

    return result.get();
  }
}
