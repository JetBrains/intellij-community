// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.file;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TemporaryDirectory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;


@RunWith(Parameterized.class)
public class FileAdapterGetPageTextTest4 {
  @Rule
  public final TemporaryDirectory tempDir = new TemporaryDirectory();

  @Test
  public void testGetPageText() throws IOException {
    Path tempFile = null;
    try {
      tempFile = tempDir.newPath("test.txt");
      try (OutputStream stream = Files.newOutputStream(tempFile)) {
        if (writeBOM) {
          stream.write(CharsetToolkit.getPossibleBom(charset));
        }
        stream.write(fileText.getBytes(charset));
      }

      VirtualFile virtualFile = new MockVirtualFile(tempFile.toFile());
      assertThat(virtualFile).isNotNull();

      virtualFile.setCharset(charset);
      FileAdapter fileAdapter = new FileAdapter(pageSize, maxBorderShift, virtualFile);
      if (!writeBOM) {
        fileAdapter.setCharset(charset);
      }

      assertEquals(expectedPages.length, fileAdapter.getPagesAmount());
      for (int i = 0; i < expectedPages.length; i++) {
        assertEquals("page[" + i + "]", expectedPages[i], fileAdapter.getPageText(i));
      }

      fileAdapter.closeFile();
    }
    finally {
      if (tempFile != null) {
        FileUtil.delete(tempFile);
        assertThat(tempFile).doesNotExist();
      }
    }
  }

  @Parameter
  public String fileText;
  @Parameter(1)
  public Charset charset;
  @Parameter(2)
  public boolean writeBOM;
  @Parameter(3)
  public int pageSize;
  @Parameter(4)
  public int maxBorderShift;
  @Parameter(5)
  public String[] expectedPages;

  @Parameters(name = " {index}: fileText=\"{0}\",charset={1},writeBOM={2},pageSize={3},maxBorderShiftSize={4},expectedPagesTexts={5}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][]{
      { // case number 0
        "str1\nstr2\n",
        StandardCharsets.UTF_8, false,
        5, 4,
        new String[]{
          "str1\n", "str2\n"}
      },
      { // case number 1
        "str1\nstr2\n",
        StandardCharsets.UTF_8, false,
        3, 0,
        new String[]{
          "str", "1\ns", "tr2", "\n"}
      },
      { // case number 2
        "str1\nstr2\n",
        StandardCharsets.UTF_8, false,
        10, 0,
        new String[]{
          "str1\nstr2\n"}
      },
      { // case number 3
        "str1\nstr2\n",
        StandardCharsets.UTF_8, false,
        9, 4,
        new String[]{
          "str1\nstr2\n", ""}
      },
      { // case number 4
        "ыыы1\nstr2\n",
        StandardCharsets.UTF_8, false,
        3, 0,
        new String[]{
          "ыы", "ы", "1\ns", "tr2", "\n"}
      },
      { // case number 5
        "ыыы1\nstr2\n",
        StandardCharsets.UTF_8, false,
        3, 0,
        new String[]{
          "ыы", "ы", "1\ns", "tr2", "\n"}
      },
      { // case number 6
        "str1\nstr2\n",
        CharsetToolkit.UTF_32BE_CHARSET, false,
        16, 0,
        new String[]{
          "str1", "\nstr", "2\n"}
      },
      { // case number 7
        "str1\nstr2\n",
        CharsetToolkit.UTF_32BE_CHARSET, true,
        16, 0,
        new String[]{
          "str", "1\nst", "r2\n"}
      },
      { // case number 8
        "ыыы1\nstr2\n",
        CharsetToolkit.UTF_32LE_CHARSET, true,
        16, 0,
        new String[]{
          "ыыы", "1\nst", "r2\n"}
      },
      { // case number 9
        "ыыы1\nstr2\n",
        CharsetToolkit.UTF_16LE_CHARSET, false,
        8, 0,
        new String[]{
          "ыыы1", "\nstr", "2\n"}
      },
      { // case number 10
        "ыыы1\nstr2\n",
        CharsetToolkit.UTF_16BE_CHARSET, false,
        8, 0,
        new String[]{
          "ыыы1", "\nstr", "2\n"}
      },
      { // case number 11
        "\uD840\uDC00" + "\uD840\uDC00" + "\uD840\uDC00",
        CharsetToolkit.UTF_16BE_CHARSET, false,
        5, 0,
        new String[]{
          "\uD840\uDC00" + "\uD840\uDC00", "\uD840\uDC00", ""}
      },
      { // case number 12
        "\uD840\uDC00" + "\uD840\uDC00" + "\uD840\uDC00",
        CharsetToolkit.UTF_16BE_CHARSET, false,
        6, 0,
        new String[]{
          "\uD840\uDC00" + "\uD840\uDC00", "\uD840\uDC00"}
      },
      { // case number 13
        "\uD840\uDC00" + "\uD840\uDC00" + "\uD840\uDC00",
        CharsetToolkit.UTF_16BE_CHARSET, false,
        7, 0,
        new String[]{
          "\uD840\uDC00" + "\uD840\uDC00", "\uD840\uDC00"}
      },
      { // case number 14
        "\uD840\uDC00" + "\uD840\uDC00" + "\uD840\uDC00" + "1\nstr2\n",
        CharsetToolkit.UTF_16BE_CHARSET, false,
        8, 0,
        new String[]{
          "\uD840\uDC00" + "\uD840\uDC00", "\uD840\uDC00" + "1\n", "str2", "\n"}
      },
      { // case number 15
        "\uD840\uDC00" + "\uD840\uDC00" + "\uD840\uDC00",
        CharsetToolkit.UTF_16BE_CHARSET, false,
        2, 0,
        new String[]{
          "\uD840\uDC00", "", "\uD840\uDC00", "", "\uD840\uDC00", ""}
      },
      { // case number 16
        "\uD840\uDC00" + "\uD840\uDC00" + "\uD840\uDC00",
        CharsetToolkit.UTF_16LE_CHARSET, false,
        5, 0,
        new String[]{
          "\uD840\uDC00" + "\uD840\uDC00", "\uD840\uDC00", ""}
      },
      { // case number 17
        "\uD840\uDC00" + "\uD840\uDC00" + "\uD840\uDC00",
        CharsetToolkit.UTF_16LE_CHARSET, false,
        6, 0,
        new String[]{
          "\uD840\uDC00" + "\uD840\uDC00", "\uD840\uDC00"}
      },
      { // case number 18
        "\uD840\uDC00" + "\uD840\uDC00" + "\uD840\uDC00",
        CharsetToolkit.UTF_16LE_CHARSET, false,
        7, 0,
        new String[]{
          "\uD840\uDC00" + "\uD840\uDC00", "\uD840\uDC00"}
      },
      { // case number 19
        "\uD840\uDC00" + "\uD840\uDC00" + "\uD840\uDC00" + "1\nstr2\n",
        CharsetToolkit.UTF_16LE_CHARSET, false,
        8, 0,
        new String[]{
          "\uD840\uDC00" + "\uD840\uDC00", "\uD840\uDC00" + "1\n", "str2", "\n"}
      },
      { // case number 20
        "ыыы1\nstr2\n",
        CharsetToolkit.UTF_16BE_CHARSET, false,
        8, 0,
        new String[]{
          "ыыы1", "\nstr", "2\n"}
      },
      { // case number 21
        "ыыы1\nstr2\n",
        CharsetToolkit.UTF_16BE_CHARSET, false,
        8, 6,
        new String[]{
          "ыыы1\n", "str2\n", ""}
      },
      { // case number 22
        "ыыы1\rstr2\r",
        CharsetToolkit.UTF_16BE_CHARSET, false,
        8, 6,
        new String[]{
          "ыыы1\r", "str2\r", ""}
      },
      { // case number 23
        "ыыы1\r\nstr2\r\n",
        CharsetToolkit.UTF_16BE_CHARSET, false,
        8, 6,
        new String[]{
          "ыыы1\r\n", "str2\r", "\n"}
      },
    });
  }
}