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
package com.intellij.codeInsight.documentation;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.javadoc.JavaDocExternalFilter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.LightCodeInsightTestCase;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * @author Denis Zhdanov
 * @since 1/15/13 7:26 PM
 */
public class RefConvertorsTest extends LightCodeInsightTestCase {

  private File myExtractedImagesDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    String tempDirectory = FileUtilRt.getTempDirectory();
    myExtractedImagesDir = new File(tempDirectory, AbstractExternalFilter.QUICK_DOC_DIR_NAME);
  }

  @Override
  protected void tearDown() throws Exception {
    FileUtil.delete(myExtractedImagesDir);
    super.tearDown();
  }

  public void testImgInsideJar() throws Exception {
    String imgJarName = "test-img";
    File imgJar = new File(myExtractedImagesDir, imgJarName + ".jar");
    boolean exist = FileUtil.createIfDoesntExist(imgJar);
    assertTrue(exist);

    JarOutputStream out = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(imgJar)));
    try {
      out.putNextEntry(new JarEntry("resources/inherit.gif"));
      FileInputStream fIn = new FileInputStream(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/documentation/inherit.gif");
      try {
        FileUtil.copy(fIn, out);
      }
      finally {
        fIn.close();
      }
    }
    finally {
      out.close();
    }

    String textBefore =
      "<HTML>" +
      "java.lang.Object\n" +
      "  <IMG SRC=\"../../../resources/inherit.gif\" ALT=\"extended by \"><B>org.bouncycastle.asn1.BERSequenceParser</B>\n" +
      "</HTML>";

    File f = new File(myExtractedImagesDir, imgJarName);
    f = new File(f, "resources");
    File extractedImgFile = new File(f, "inherit.gif");
    String expectedTextAfter = String.format(
      "<HTML>" +
      "java.lang.Object\n" +
      "  <IMG SRC=\"%s%s\" ALT=\"extended by \"><B>org.bouncycastle.asn1.BERSequenceParser</B>\n" +
      "</HTML>",
      LocalFileSystem.PROTOCOL_PREFIX,
      extractedImgFile.getAbsolutePath());

    JavaDocExternalFilter filter = new JavaDocExternalFilter(getProject());
    String textAfter = filter.correctRefs(
      String.format("%s%s!/org/bouncycastle/asn1/BERSequenceParser.html", JarFileSystem.PROTOCOL_PREFIX, imgJar.getAbsolutePath()),
      textBefore
    );
    assertEquals(expectedTextAfter, textAfter);
    assertTrue(extractedImgFile.isFile());
  }
}
