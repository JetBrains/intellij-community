// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.file;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.PathKt;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class LargeFileManagerImplTest {
  @Test
  public void getPage_wait_separators() throws IOException {
    final String fileContent = "...\n...\r\n...\r...\r\n\r\r\n\n...\r\n\r...\n\r\n...\r\r...\n\n...";
    final String expectedReadedContent = "...\n...\n...\n...\n\n\n\n...\n\n...\n\n...\n\n...\n\n...";
    final Charset charset = StandardCharsets.US_ASCII;

    final int pageSize = 5;
    final int maxPageBorderShift = 4;

    File tempFile = null;
    try {
      tempFile = FileUtil.createTempFile("test", ".txt");
      PathKt.write(tempFile.toPath(), fileContent, StandardCharsets.US_ASCII);
      VirtualFile virtualFile = new MockVirtualFile(tempFile);
      assertNotNull(virtualFile);

      LargeFileManager fileManager = new LargeFileManagerImpl(virtualFile, pageSize, maxPageBorderShift);
      fileManager.reset(charset);

      StringBuilder readedContent = new StringBuilder();

      long pagesAmount = fileManager.getPagesAmount();
      for (int i = 0; i < pagesAmount; i++) {
        readedContent.append(fileManager.getPage_wait(i).getText());
      }

      assertEquals(expectedReadedContent, readedContent.toString());

      Disposer.dispose(fileManager);
    }
    finally {
      if (tempFile != null) {
        FileUtil.delete(tempFile);
        assertFalse(tempFile.exists());
      }
    }
  }
}