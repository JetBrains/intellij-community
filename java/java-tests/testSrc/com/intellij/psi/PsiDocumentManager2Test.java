// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.highlighter.XmlLikeFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

@HeavyPlatformTestCase.WrapInCommand
public class PsiDocumentManager2Test extends LightPlatformTestCase {
  public void testUnregisteredFileType() {
    class MyFileType extends XmlLikeFileType {
      private MyFileType() {
        super(XMLLanguage.INSTANCE);
      }

      @Override
      @NotNull
      public String getName() {
        return "MY";
      }

      @Override
      @NotNull
      public String getDefaultExtension() {
        return "my";
      }

      @Override
      @NotNull
      public String getDescription() {
        return "my own";
      }

      @Override
      @Nullable
      public Icon getIcon() {
        return null;
      }
    }
    PsiFile file = PsiFileFactory.getInstance(getProject()).createFileFromText("DummyFile.my", new MyFileType(), "<gggg></gggg>", LocalTimeCounter.currentTime(), true);
    Document document = Objects.requireNonNull(PsiDocumentManager.getInstance(getProject()).getDocument(file));

    assertTrue(document.isWritable());
    assertTrue(file.getVirtualFile().getFileType() instanceof MyFileType);
    assertTrue(DaemonCodeAnalyzer.getInstance(getProject()).isHighlightingAvailable(file));
  }

  public void testPsiToDocSyncInNonPhysicalFile() {
    PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(getProject())
      .createFileFromText("a.java", JavaLanguage.INSTANCE, "class Foo {}", false, false);

    Document document = FileDocumentManager.getInstance().getDocument(file.getViewProvider().getVirtualFile());
    assertNotNull(document);

    file.getClasses()[0].getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);

    assertEquals("public class Foo {}", document.getText());
    assertFalse(PsiDocumentManager.getInstance(getProject()).isUncommited(document));
  }

}
