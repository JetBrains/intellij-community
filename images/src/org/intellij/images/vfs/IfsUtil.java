/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/** $Id$ */

package org.intellij.images.vfs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.reference.SoftReference;
import com.intellij.util.LogicalRoot;
import com.intellij.util.LogicalRootsManager;
import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.common.byteSources.ByteSourceArray;
import org.apache.sanselan.formats.ico.IcoImageParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * Image loader utility.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public final class IfsUtil {
  public static final String ICO_FORMAT = "ico";

  private static final Key<Long> TIMESTAMP_KEY = Key.create("Image.timeStamp");
  private static final Key<String> FORMAT_KEY = Key.create("Image.format");
  private static final Key<SoftReference<BufferedImage>> BUFFERED_IMAGE_REF_KEY = Key.create("Image.bufferedImage");
  private static final IcoImageParser ICO_IMAGE_PARSER = new IcoImageParser();

  /**
   * Load image data for file and put user data attributes into file.
   *
   * @param file File
   * @return true if file image is loaded.
   * @throws java.io.IOException if image can not be loaded
   */
  private static boolean refresh(@NotNull VirtualFile file) throws IOException {
    Long loadedTimeStamp = file.getUserData(TIMESTAMP_KEY);
    SoftReference<BufferedImage> imageRef = file.getUserData(BUFFERED_IMAGE_REF_KEY);
    if (loadedTimeStamp == null || loadedTimeStamp.longValue() != file.getTimeStamp() || SoftReference.dereference(imageRef) == null) {
      try {
        final byte[] content = file.contentsToByteArray();

        if (ICO_FORMAT.equalsIgnoreCase(file.getExtension())) {
          try {
            final BufferedImage image = ICO_IMAGE_PARSER.getBufferedImage(new ByteSourceArray(content), null);
            file.putUserData(FORMAT_KEY, ICO_FORMAT);
            file.putUserData(BUFFERED_IMAGE_REF_KEY, new SoftReference<>(image));
            return true;
          }
          catch (ImageReadException ignore) { }
        }

        InputStream inputStream = new ByteArrayInputStream(content, 0, content.length);
        ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream);
        try {
          Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(imageInputStream);
          if (imageReaders.hasNext()) {
            ImageReader imageReader = imageReaders.next();
            try {
              file.putUserData(FORMAT_KEY, imageReader.getFormatName());
              ImageReadParam param = imageReader.getDefaultReadParam();
              imageReader.setInput(imageInputStream, true, true);
              int minIndex = imageReader.getMinIndex();
              BufferedImage image = imageReader.read(minIndex, param);
              file.putUserData(BUFFERED_IMAGE_REF_KEY, new SoftReference<>(image));
              return true;
            } finally {
              imageReader.dispose();
            }
          }
        } finally {
          imageInputStream.close();
        }
      } finally {
        // We perform loading no more needed
        file.putUserData(TIMESTAMP_KEY, file.getTimeStamp());
      }
    }
    return false;
  }

  @Nullable
  public static BufferedImage getImage(@NotNull VirtualFile file) throws IOException {
    refresh(file);
    SoftReference<BufferedImage> imageRef = file.getUserData(BUFFERED_IMAGE_REF_KEY);
    return SoftReference.dereference(imageRef);
  }

  @Nullable
  public static String getFormat(@NotNull VirtualFile file) throws IOException {
    refresh(file);
    return file.getUserData(FORMAT_KEY);
  }

  public static String getReferencePath(Project project, VirtualFile file) {
    final LogicalRoot logicalRoot = LogicalRootsManager.getLogicalRootsManager(project).findLogicalRoot(file);
    if (logicalRoot != null) {
      return getRelativePath(file, logicalRoot.getVirtualFile());
    }

    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    VirtualFile sourceRoot = fileIndex.getSourceRootForFile(file);
    if (sourceRoot != null) {
      return getRelativePath(file, sourceRoot);
    }

    VirtualFile root = fileIndex.getContentRootForFile(file);
    if (root != null) {
      return getRelativePath(file, root);
    }

    return file.getPath();
  }

  private static String getRelativePath(final VirtualFile file, final VirtualFile root) {
    if (root.equals(file)) {
      return file.getPath();
    }
    return "/" + VfsUtilCore.getRelativePath(file, root, '/');
  }
}
