// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.fileTypes;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import org.intellij.images.ImagesBundle;
import org.intellij.images.index.ImageInfoIndex;
import org.intellij.images.util.ImageInfo;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;

public class ImageDocumentationProvider extends AbstractDocumentationProvider {
  private static final int MAX_IMAGE_SIZE = 300;

  @Override
  public @Nls String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    if (!(element instanceof PsiFileSystemItem) || ((PsiFileSystemItem)element).isDirectory()) {
      return null;
    }
    final VirtualFile file = ((PsiFileSystemItem)element).getVirtualFile();
    if (file == null) {
      return null;
    }
    ImageInfo imageInfo = ImageInfoIndex.getInfo(file, element.getProject());
    if (imageInfo == null) {
      return null;
    }
    int imageWidth = imageInfo.width;
    int imageHeight = imageInfo.height;

    int maxSize = Math.max(imageInfo.width, imageInfo.height);
    if (maxSize > MAX_IMAGE_SIZE) {
      double scaleFactor = (double)MAX_IMAGE_SIZE / (double)maxSize;
      imageWidth *= scaleFactor;
      imageHeight *= scaleFactor;
    }
    try {
      String path = file.getPath();
      if (SystemInfo.isWindows) {
        path = "/" + path;
      }
      final String url = new URI("file", null, path, null).toString();
      HtmlChunk.Element img = HtmlChunk.tag("img")
        .attr("src", url)
        .attr("width", imageWidth)
        .attr("height", imageHeight);
      String message = ImagesBundle.message("image.description", imageInfo.width, imageInfo.height, imageInfo.bpp);
      return new HtmlBuilder().append(img).append(HtmlChunk.p().addText(message)).toString();
    }
    catch (URISyntaxException ignored) {
      return null;
    }
  }
}
