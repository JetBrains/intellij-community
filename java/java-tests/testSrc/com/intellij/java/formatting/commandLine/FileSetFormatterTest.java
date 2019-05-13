/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.formatting.commandLine;

import com.intellij.JavaTestUtil;
import com.intellij.formatting.commandLine.FileSetFormatter;
import com.intellij.formatting.commandLine.MessageOutput;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class FileSetFormatterTest extends LightPlatformTestCase {
  private static final String BASE_PATH = JavaTestUtil.getJavaTestDataPath() + "/psi/formatter/commandLine";

  public void testFormat() throws IOException {
    CodeStyleSettings settings = new CodeStyleSettings();
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.getIndentOptions().INDENT_SIZE = 2;
    javaSettings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    javaSettings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
    File sourceDir = createSourceDir("original");
    String fileSpec = sourceDir.getCanonicalPath();
    MessageOutput messageOutput = new MessageOutput(new PrintWriter(System.out), new PrintWriter(System.err));
    FileSetFormatter formatter = new FileSetFormatter(messageOutput);
    formatter.addEntry(fileSpec);
    formatter.addFileMask("*.java");
    formatter.setRecursive();
    formatter.setCodeStyleSettings(settings);
    formatter.processFiles();
    compareDirs(new File(BASE_PATH + File.separator + "expected"), sourceDir);
  }

  private static File createSourceDir(@NotNull String subDir) throws IOException {
    File sourceDir = FileUtil.createTempDirectory("unitTest", "_format");
    FileUtil.copyDir(new File(BASE_PATH + File.separator + subDir), sourceDir);
    return sourceDir;
  }

  private static void compareDirs(@NotNull File expectedDir, @NotNull File resultDir) throws IOException {
    assertTrue(expectedDir.isDirectory() && resultDir.isDirectory());
    File[] childFiles = expectedDir.listFiles();
    if (childFiles != null) {
      for (File file : childFiles) {
        File resultEntry = new File(resultDir.getCanonicalPath() + File.separator + file.getName());
        assertTrue("Cannot find expected" + resultEntry.getPath(), resultEntry.exists());
        if (!file.isDirectory()) {
          assertContentEquals(file, resultEntry);
        }
        else {
          compareDirs(file, resultEntry);
        }
      }
    }
  }

  private static void assertContentEquals(@NotNull File expectedFile, @NotNull File actualFile) throws IOException {
    VirtualFile expectedVFile = VfsUtil.findFileByIoFile(expectedFile, true);
    assertNotNull(expectedVFile);
    VirtualFile actualVFile = VfsUtil.findFileByIoFile(actualFile, true);
    assertNotNull(actualVFile);
    String expected = VfsUtilCore.loadText(expectedVFile);
    String actual = VfsUtilCore.loadText(actualVFile);
    assertEquals(expected, actual);
  }
}
