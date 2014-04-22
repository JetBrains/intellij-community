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
package com.intellij.util.indexing;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.newvfs.persistent.ContentHashesUtil;
import com.intellij.openapi.vfs.newvfs.persistent.FlushingDaemon;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.MessageDigest;

/**
 * @author Maxim.Mossienko
 * @since 4/10/2014.
 */
class ContentHashesSupport {
  private static final ContentHashesUtil.HashEnumerator ourHashesWithFileType;

  static {
    ContentHashesUtil.HashEnumerator hashEnumerator = null;
    try {
      final File hashEnumeratorFile = new File(PathManager.getIndexRoot(), "hashesWithFileType");
      hashEnumerator = IOUtil.openCleanOrResetBroken(new ThrowableComputable<ContentHashesUtil.HashEnumerator, IOException>() {
        @Override
        public ContentHashesUtil.HashEnumerator compute() throws IOException {
          return new ContentHashesUtil.HashEnumerator(hashEnumeratorFile, null);
        }
      }, hashEnumeratorFile);
      FlushingDaemon.everyFiveSeconds(new Runnable() {
        @Override
        public void run() {
          if (ourHashesWithFileType.isDirty()) ourHashesWithFileType.force();
        }
      });
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    } finally {
      ourHashesWithFileType = hashEnumerator;
    }
  }


  static int calcContentHashIdWithFileType(@NotNull byte[] bytes, @NotNull FileType fileType) throws IOException {
    MessageDigest messageDigest = ContentHashesUtil.HASHER_CACHE.getValue();

    Charset defaultCharset = Charset.defaultCharset();
    messageDigest.update(fileType.getName().getBytes(defaultCharset));
    messageDigest.update((byte)0);
    messageDigest.update(String.valueOf(bytes.length).getBytes(defaultCharset));
    messageDigest.update((byte)0);
    messageDigest.update(bytes, 0, bytes.length);
    byte[] digest = messageDigest.digest();

    return ourHashesWithFileType.enumerate(digest);
  }
}
