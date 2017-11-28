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

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.MockLanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.PsiExpressionCodeFragmentImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.text.BlockSupport;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class JavaReparseTest extends AbstractReparseTestCase {

  public void testInsertLBrace() {
    setFileType(StdFileTypes.JAVA);
    final String text2 = "}}}";
    final String text1 = "class Foo{ void method(){{";
    prepareFile(text1, text2);
    insert("{");
  }

  public void testJavaDoc() {
    setFileType(StdFileTypes.JAVA);
    String text2 = "void method() {}}";
    final String text1 = "class Foo { /** ";
    prepareFile(text1, text2);
    insert("*/");
  }

  public void testSCR5665() {
    setFileType(StdFileTypes.JAVA);
    final String text2 = " \"and then insert it again\"}\n" +
                         "  };\n" +
                         "}";
    final String text1 = "class RedTest {\n" +
                         "  String[][] test = {\n" +
                         "    {\"remove the comma >\",";
    prepareFile(text1, text2);
    remove(1);
    insert(",");
    PsiFile file1 = myDummyFile;
    prepareFile(text1, text2);
    assertEquals(DebugUtil.treeToString(SourceTreeToPsiMap.psiElementToTree(file1), false),
                 DebugUtil.treeToString(SourceTreeToPsiMap.psiElementToTree(myDummyFile), false));
  }


  @NotNull
  @Override
  protected PsiFile createDummyFile(@NotNull String fileName, @NotNull String text) throws IncorrectOperationException {
    if (getTestName(true).equals("codeFragment")) return new PsiExpressionCodeFragmentImpl(getProject(), true, "fragment.java", text, null,
                                                                                           null);
    return super.createDummyFile(fileName, text);
  }

  public void testCodeFragment() {
    setFileType(StdFileTypes.JAVA);
    prepareFile("a", "a");
    insert("xxx xxx xxx xxx xxx xxx xxx");
  }

  public void testReparseAfterReformatReplacesWhitespaceNodesOnly() {
    @NonNls final String text =
      "class  RedTest   {   \n\n\n\n\n\n\n\n   " +
      "String  [  ]  [  ]   test    =    {       { \n\n\n\n\n {    \"\"}  \n\n\n\n\n };   " +
      "String  [  ]  [  ]   test    =    {       { \n\n\n\n\n {    \"\"}  \n\n\n\n\n };   " +
      "                      \n\n\n\n\n\n\n\n  }  ";

    final PsiFile file = myFixture.addFileToProject("aaa.java", text);
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
    }, getProject());

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      CodeStyleManager.getInstance(getProject()).reformatText(file, 0, file.getTextLength());
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    });

    assertEquals(0, added[0]);
    assertEquals(0, removed[0]);
    assertEquals(0, moved[0]);
    assertEquals(0, replacedNWhite[0]);
    assertTrue(0 != replacedWhite[0]);
  }

  public void testInsertXMLSubTagProducesAddEvents() {
    @NonNls @Language("XML")
    final String text = "<preface>\n" +
                        "     <para>\n" +
                        "         TODO\n" +
                        "     </para>\n" +
                        "\n" +
                        "\n" +
                        "</preface>\n" +
                        "";

    final PsiFile file = myFixture.addFileToProject("aaa.xml", text);
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

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
    }, getTestRootDisposable());

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
      int offset = document.getText().indexOf("</para>\n\n") + "</para>\n\n".length();
      document.insertString(offset, "<another></another>");
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    });

    assertEquals(2, added.size());
    assertTrue(added.get(0) instanceof XmlTag);
    assertEquals("<another></another>", added.get(0).getText());
    assertTrue(added.get(1) instanceof XmlText);
    assertEquals("\n", added.get(1).getText());
    assertEmpty(removed);
    assertEmpty(moved);
    assertEmpty(replaced);
  }

  public void testPlainTextSubstitution() {
    LightVirtualFile vFile = new LightVirtualFile("a.xxx", MockLanguageFileType.INSTANCE, "aaa");
    PsiFile file = myDummyFile = PsiManager.getInstance(getProject()).findFile(vFile);
    assertNotNull(file);

    assertEquals(com.intellij.lang.Language.ANY, file.getViewProvider().getBaseLanguage());
    assertEquals(PlainTextLanguage.INSTANCE, file.getLanguage());
    assertEquals(file, file.getViewProvider().getPsi(file.getViewProvider().getBaseLanguage()));

    doReparse("b", 0);
    assertTrue("baaa", file.isValid());
    assertEquals("baaa", file.getText());
    assertEquals("baaa", file.getFirstChild().getText());
  }

  public void testOverlappingCommonPrefixAndSuffix() {
    setFileType(StdFileTypes.JAVA);
    String toRemove = "} {foobar";
    prepareFile("class Foo { {goo} {foobar" + toRemove, " foobar} }");
    remove(toRemove.length());
  }

  public void testDocComment() {
    String text = "/** .../ */";
    final int offset = text.indexOf("...");
    myDummyFile = createDummyFile(getName() + ".java", text);
    String treeBefore = DebugUtil.psiTreeToString(myDummyFile, true);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      BlockSupport.getInstance(getProject()).reparseRange(myDummyFile, offset, offset + 3, "*");
      BlockSupport.getInstance(getProject()).reparseRange(myDummyFile, offset, offset + 1, "...");
    });
    String treeAfter = DebugUtil.psiTreeToString(myDummyFile, true);
    assertEquals(treeBefore, treeAfter);
  }
}
