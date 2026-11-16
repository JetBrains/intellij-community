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
package com.intellij.java.psi;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.MockLanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.ReparseFixture;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.PsiExpressionCodeFragmentImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.text.BlockSupport;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.ReparseFixtureKt.reparseFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaReparseTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<ReparseFixture> reparseFixture = reparseFixture(projectFixture, null, (reparse, fileName, text) -> {
    if (reparse.getTestName(true).equals("codeFragment")) return new PsiExpressionCodeFragmentImpl(reparse.getProject(), true, "fragment.java", text, null,
                                                                                                    null);
    return reparse.createDefaultDummyFile(fileName, text);
  });

  @Test
  public void testInsertLBrace() {
    ReparseFixture reparse = reparseFixture.get();
    reparse.setFileType(JavaFileType.INSTANCE);
    final String text2 = "}}}";
    final String text1 = "class Foo{ void method(){{";
    reparse.prepareFile(text1, text2);
    reparse.insert("{");
  }

  @Test
  public void testJavaDoc() {
    ReparseFixture reparse = reparseFixture.get();
    reparse.setFileType(JavaFileType.INSTANCE);
    String text2 = "void method() {}}";
    final String text1 = "class Foo { /** ";
    reparse.prepareFile(text1, text2);
    reparse.insert("*/");
  }

  @Test
  public void testSCR5665() {
    ReparseFixture reparse = reparseFixture.get();
    reparse.setFileType(JavaFileType.INSTANCE);
    final String text2 = """
       "and then insert it again"}
        };
      }""";
    final String text1 = """
      class RedTest {
        String[][] test = {
          {"remove the comma >",""";
    reparse.prepareFile(text1, text2);
    reparse.remove(1);
    reparse.insert(",");
    PsiFile file1 = reparse.getDummyFile();
    reparse.prepareFile(text1, text2);
    ReadAction.runBlocking(() -> {
      assertEquals(DebugUtil.treeToString(SourceTreeToPsiMap.psiElementToTree(file1), true),
                   DebugUtil.treeToString(SourceTreeToPsiMap.psiElementToTree(reparse.getDummyFile()), true));
    });
  }

  @Test
  public void testCodeFragment() {
    ReparseFixture reparse = reparseFixture.get();
    reparse.setFileType(JavaFileType.INSTANCE);
    reparse.prepareFile("a", "a");
    reparse.insert("xxx xxx xxx xxx xxx xxx xxx");
  }

  @Test
  public void testReparseAfterReformatReplacesWhitespaceNodesOnly() {
    ReparseFixture reparse = reparseFixture.get();
    @NonNls final String text =
      """
        class  RedTest   {  \s







           String  [  ]  [  ]   test    =    {       {\s




         {    ""} \s




         };   String  [  ]  [  ]   test    =    {       {\s




         {    ""} \s




         };                        \s







          } \s""";

    final PsiFile file = reparse.getFixture().addFileToProject("reparseAfterReformat.java", text);
    final int[] added = {0};
    final int[] removed = {0};
    final int[] replacedWhite = {0};
    final int[] replacedNWhite = {0};
    final int[] moved = {0};

    file.getManager().addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
      @Override
      public void childAdded(@NotNull PsiTreeChangeEvent event) {
        PsiElement oldElement = event.getChild();
        if (oldElement instanceof PsiWhiteSpace) replacedWhite[0]++; else added[0]++;
      }

      @Override
      public void childRemoved(@NotNull PsiTreeChangeEvent event) {
        PsiElement oldElement = event.getOldChild();
        if (oldElement instanceof PsiWhiteSpace) replacedWhite[0]++; else removed[0]++;
      }

      @Override
      public void childReplaced(@NotNull PsiTreeChangeEvent event) {
        PsiElement oldElement = event.getOldChild();
        PsiElement newElement = event.getNewChild();

        if (oldElement instanceof PsiWhiteSpace) {
          replacedWhite[0]++;
        }
        else {
          replacedNWhite[0]++;
        }
        if (newElement instanceof PsiWhiteSpace) {
          replacedWhite[0]++;
        }
        else {
          replacedNWhite[0]++;
        }
      }

      @Override
      public void childMoved(@NotNull PsiTreeChangeEvent event) {
        moved[0]++;
      }
    }, reparse.getFixture().getProjectDisposable());

    WriteCommandAction.runWriteCommandAction(reparse.getProject(), () -> {
      CodeStyleManager.getInstance(reparse.getProject()).reformatText(file, 0, file.getTextLength());
      PsiDocumentManager.getInstance(reparse.getProject()).commitAllDocuments();
    });

    assertEquals(0, added[0]);
    assertEquals(0, removed[0]);
    assertEquals(0, moved[0]);
    assertEquals(0, replacedNWhite[0]);
    assertTrue(0 != replacedWhite[0]);
  }

  @Test
  public void testInsertXMLSubTagProducesAddEvents() {
    ReparseFixture reparse = reparseFixture.get();
    @NonNls @Language("XML")
    final String text = """
      <preface>
           <para>
               TODO
           </para>


      </preface>
      """;

    final PsiFile file = reparse.getFixture().addFileToProject("aaa.xml", text);
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(reparse.getProject()).commitAllDocuments());

    final List<PsiElement> added = new ArrayList<>();
    final List<PsiElement> removed = new ArrayList<>();
    final List<PsiElement> replaced = new ArrayList<>();
    final List<PsiElement> moved = new ArrayList<>();
    file.getManager().addPsiTreeChangeListener(new PsiTreeChangeAdapter(){
      @Override
      public void childAdded(@NotNull PsiTreeChangeEvent event) {
        added.add(event.getChild());
      }

      @Override
      public void childRemoved(@NotNull PsiTreeChangeEvent event) {
        removed.add(event.getElement());
      }

      @Override
      public void childReplaced(@NotNull PsiTreeChangeEvent event) {
        replaced.add(event.getOldChild());
      }

      @Override
      public void childMoved(@NotNull PsiTreeChangeEvent event) {
        moved.add(event.getElement());
      }
    }, reparse.getFixture().getTestRootDisposable());

    WriteCommandAction.runWriteCommandAction(reparse.getProject(), () -> {
      Document document = PsiDocumentManager.getInstance(reparse.getProject()).getDocument(file);
      int offset = document.getText().indexOf("</para>\n\n") + "</para>\n\n".length();
      document.insertString(offset, "<another></another>");
      PsiDocumentManager.getInstance(reparse.getProject()).commitAllDocuments();
    });

    ReadAction.runBlocking(() -> {
      assertEquals(2, added.size());
      assertInstanceOf(XmlTag.class, added.get(0));
      assertEquals("<another></another>", added.get(0).getText());
      assertInstanceOf(XmlText.class, added.get(1));
      assertEquals("\n", added.get(1).getText());
    });
    assertTrue(removed.isEmpty(), removed::toString);
    assertTrue(moved.isEmpty(), moved::toString);
    assertTrue(replaced.isEmpty(), replaced::toString);
  }

  @Test
  public void testPlainTextSubstitution() {
    ReparseFixture reparse = reparseFixture.get();
    LightVirtualFile vFile = new LightVirtualFile("a.xxx", MockLanguageFileType.INSTANCE, "aaa");
    PsiFile file = ReadAction.computeBlocking(() -> PsiManager.getInstance(reparse.getProject()).findFile(vFile));
    reparse.setDummyFile(file);
    assertNotNull(file);

    ReadAction.runBlocking(() -> {
      assertEquals(com.intellij.lang.Language.ANY, file.getViewProvider().getBaseLanguage());
      assertEquals(PlainTextLanguage.INSTANCE, file.getLanguage());
      assertEquals(file, file.getViewProvider().getPsi(file.getViewProvider().getBaseLanguage()));
    });

    reparse.doReparse("b", 0);
    ReadAction.runBlocking(() -> {
      assertTrue(file.isValid(), "baaa");
      assertEquals("baaa", file.getText());
      assertEquals("baaa", file.getFirstChild().getText());
    });
  }

  @Test
  public void testOverlappingCommonPrefixAndSuffix() {
    ReparseFixture reparse = reparseFixture.get();
    reparse.setFileType(JavaFileType.INSTANCE);
    String toRemove = "} {foobar";
    reparse.prepareFile("class Foo { {goo} {foobar" + toRemove, " foobar} }");
    reparse.remove(toRemove.length());
  }

  @Test
  public void testDocComment() {
    ReparseFixture reparse = reparseFixture.get();
    String text = "/** .../ */";
    final int offset = text.indexOf("...");
    EdtTestUtil.runInEdtAndWait(() -> {
      reparse.setDummyFile(reparse.createDummyFile(reparse.getName() + ".java", text));
      String treeBefore = DebugUtil.psiTreeToString(reparse.getDummyFile(), false);
      WriteCommandAction.runWriteCommandAction(reparse.getProject(), () -> {
        BlockSupport.getInstance(reparse.getProject()).reparseRange(reparse.getDummyFile(), offset, offset + 3, "*");
        BlockSupport.getInstance(reparse.getProject()).reparseRange(reparse.getDummyFile(), offset, offset + 1, "...");
      });
      String treeAfter = DebugUtil.psiTreeToString(reparse.getDummyFile(), false);
      assertEquals(treeBefore, treeAfter);
    });
  }
}
