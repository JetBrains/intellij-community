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
package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.source.tree.LazyParseableElement;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class MiscPsiTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected void invokeTestRunnable(final Runnable runnable) throws Exception {
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        runnable.run();
      }
    }.execute();
  }

  public void testCopyTextFile() throws Exception{
    String text = "1234567890";
    PsiFile file = myFixture.addFileToProject("Test.txt", text);

    VirtualFile vDir = myFixture.getTempDirFixture().findOrCreateDir("dir");

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertTrue(file instanceof PsiPlainTextFile);
    PsiDirectory dir = getPsiManager().findDirectory(vDir);
    PsiFile fileCopy = (PsiFile)file.copy();
    fileCopy = (PsiFile) fileCopy.setName("NewTest.txt");
    PsiFile newFile = (PsiFile)dir.add(fileCopy);
    assertTrue(newFile instanceof PsiPlainTextFile);

    assertEquals(text, new String(newFile.getVirtualFile().contentsToByteArray()));
    assertEquals(newFile.getVirtualFile().getModificationStamp(), newFile.getViewProvider().getModificationStamp());
    Document document = PsiDocumentManager.getInstance(getProject()).getDocument(newFile);
    assertEquals(newFile.getVirtualFile().getModificationStamp(), document.getModificationStamp());
  }

  public void testCopyBinaryFile() throws Exception{
    VirtualFile vFile = myFixture.addFileToProject("Test.xxx", "").getVirtualFile();
    final byte[] bytes = {12,34,56,78,90,45,83,0x22,(byte)0xff, (byte)0xff, (byte)0xff, (byte)0xee};
    vFile.setBinaryContent(bytes);

    VirtualFile vDir = myFixture.getTempDirFixture().findOrCreateDir("dir");

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PsiFile file = getPsiManager().findFile(vFile);
    assertTrue(file instanceof PsiBinaryFile);
    PsiDirectory dir = getPsiManager().findDirectory(vDir);
    PsiFile fileCopy = (PsiFile)file.copy();
    fileCopy = (PsiFile) fileCopy.setName("NewTest.xxx");
    PsiFile newFile = (PsiFile)dir.add(fileCopy);
    assertTrue(newFile instanceof PsiBinaryFile);

    assertOrderedEquals(newFile.getVirtualFile().contentsToByteArray(), bytes);
  }

  public void testCopyBinaryToTextFile() throws Exception{
    String text = "1234567890";
    VirtualFile vFile = myFixture.addFileToProject("Test.xxx", text).getVirtualFile();

    VirtualFile vDir = myFixture.getTempDirFixture().findOrCreateDir("dir");

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PsiFile file = getPsiManager().findFile(vFile);
    PsiDirectory dir = getPsiManager().findDirectory(vDir);
    PsiFile fileCopy = (PsiFile)file.copy();
    fileCopy = (PsiFile) fileCopy.setName("NewTest.txt");
    PsiFile newFile = (PsiFile)dir.add(fileCopy);
    assertTrue(newFile instanceof PsiPlainTextFile);

    assertEquals(text, VfsUtil.loadText(newFile.getVirtualFile()));
    assertEquals(newFile.getVirtualFile().getModificationStamp(), newFile.getViewProvider().getModificationStamp());
    assertFalse(FileDocumentManager.getInstance().isFileModified(newFile.getVirtualFile()));
  }

  public void testSCR4212() throws Exception{
    String text = "class A{{ return(Object)new String(); }}";
    VirtualFile vFile = myFixture.addFileToProject("Test.java", text).getVirtualFile();

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PsiJavaFile file = (PsiJavaFile)getPsiManager().findFile(vFile);
    PsiClass aClass = file.getClasses()[0];
    PsiClassInitializer initializer = aClass.getInitializers()[0];
    PsiStatement statement = initializer.getBody().getStatements()[0];
    PsiTypeCastExpression typeCast = (PsiTypeCastExpression)((PsiReturnStatement)statement).getReturnValue();
    typeCast.replace(typeCast.getOperand());

    String textAfter = file.getText();
    String expectedText = "class A{{ return new String(); }}";
    assertEquals(expectedText, textAfter);
  }

  public void testDeleteFieldInMultipleDeclarations() throws Exception {
    final PsiClass aClass = getJavaFacade().getElementFactory().createClassFromText("public int i, j;", null);

    final PsiField aField = aClass.getFields()[0];
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      public void run() {
        aField.delete();
      }
    });

    assertEquals("public int j;", aClass.getFields()[0].getText());
  }

  public void testSCR5929() throws Exception {
    String text = "class A{ /** @see a.B */ }";
    VirtualFile vFileA = myFixture.addFileToProject("A.java", text).getVirtualFile();

    VirtualFile dir = myFixture.getTempDirFixture().findOrCreateDir("a");
    VirtualFile vFileB = dir.createChildData(null, "B.java");
    text = "class B{}";
    VfsUtil.saveText(vFileB, text);

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PsiFile fileA = getPsiManager().findFile(vFileA);
    PsiJavaFile fileACopy = (PsiJavaFile)fileA.copy();
    PsiClass aClass = fileACopy.getClasses()[0];
    aClass.setName("A2");
    fileA.getContainingDirectory().add(fileACopy);
  }

  public void testCopyClass() throws Exception {
    String text = "package aaa; class A{}";
    VirtualFile vFile = myFixture.addFileToProject("A.java", text).getVirtualFile();

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PsiFile file = getPsiManager().findFile(vFile);
    PsiJavaFile fileACopy = (PsiJavaFile)file.copy();
    PsiClass aClass = fileACopy.getClasses()[0];
    aClass.setName("ANew");
    PsiFile newFile = (PsiFile)file.getContainingDirectory().add(fileACopy);
    Document document = PsiDocumentManager.getInstance(getProject()).getDocument(newFile);
    FileDocumentManager.getInstance().saveDocument(document);
    assertEquals(newFile.getVirtualFile().getModificationStamp(), newFile.getViewProvider().getModificationStamp());
    assertFalse(FileDocumentManager.getInstance().isFileModified(newFile.getVirtualFile()));
  }

  public void testSCR15954() throws Exception {
    PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("A.java", "class A{\nA(){}\n}");

    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(getProject());
    Document document = psiDocumentManager.getDocument(file);
    document.insertString(document.getTextLength(), " "); // insert a trailing space to strip
    psiDocumentManager.commitAllDocuments();

    PsiClass aClass = file.getClasses()[0];
    aClass.setName("B");
  }

  public void testDeleteAnnotationAttribute() throws Exception {
    final PsiAnnotation annotation = getJavaFacade().getElementFactory().createAnnotationFromText("@A(b,c)", null);
    final PsiNameValuePair secondAttribute = annotation.getParameterList().getAttributes()[1];
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        secondAttribute.delete();
      }
    });

    assertEquals("@A(b )", annotation.getText());
  }

  public void testDeleteAnnotationArrayInitializerElement() throws Exception {
    final PsiAnnotation annotation = getJavaFacade().getElementFactory().createAnnotationFromText("@A({b,c})", null);
    final PsiNameValuePair firstAttribute = annotation.getParameterList().getAttributes()[0];
    assertTrue(firstAttribute.getValue() instanceof PsiArrayInitializerMemberValue);
    final PsiAnnotationMemberValue firstInitializer = ((PsiArrayInitializerMemberValue)firstAttribute.getValue()).getInitializers()[0];
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        firstInitializer.delete();
      }
    });

    assertEquals("@A({ c})", annotation.getText());
  }

  private JavaPsiFacade getJavaFacade() {
    return JavaPsiFacade.getInstance(getProject());
  }

  public void testJavaLangObjectSuperMethod() throws Exception {
    final PsiClass aClass =
      getJavaFacade().getElementFactory().createClassFromText("public String toString() {return null;}", null);
    final PsiMethod method = aClass.getMethods()[0];
    final PsiMethod[] superMethods = method.findSuperMethods();
    assertEquals(1, superMethods.length);
    assertEquals(CommonClassNames.JAVA_LANG_OBJECT, superMethods[0].getContainingClass().getQualifiedName());
  }

  public void testImportOnDemand() throws Exception {
    final PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(getProject()).createFileFromText("D.java",
                                                                                                                      "import java.util.Map.Entry");
    PsiImportStatement importStatement = file.getImportList().getImportStatements()[0];
    assertTrue(!importStatement.isOnDemand());
  }

  public void testDocCommentPrecededByLineComment() throws Exception {
    final PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(getProject()).createFileFromText("D.java",
                                                                                                                      "////////////////////////////////////////\n" +
                                                                                                                      "/** */\n" +
                                                                                                                                 "/////////////////////////////////////////////////\n" +
                                                                                                                                                                                       "class Usage {\n" +
                                                                                                                                                                                                         "}");
    final PsiClass psiClass = file.getClasses()[0];
    assertNotNull(psiClass.getDocComment());
  }

  public void testTopLevelEnumIsNotStatic() throws Exception {
    final JavaPsiFacade facade = getJavaFacade();
    final LanguageLevel prevLanguageLevel = LanguageLevelProjectExtension.getInstance(facade.getProject()).getLanguageLevel();
    LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    final PsiClass aClass;
    try {
      aClass = JavaPsiFacade.getInstance(getProject()).getElementFactory().createEnum("E");
    }
    finally {
      LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(prevLanguageLevel);
    }
    assertTrue(aClass.isEnum());
    assertFalse(aClass.hasModifierProperty(PsiModifier.STATIC));
  }

  public void testDoNotExpandNestedChameleons() throws Exception {
    PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("a.java", "class A {{{}}}");
    file.getNode();

    PsiCodeBlock initializer = file.getClasses()[0].getInitializers()[0].getBody();
    assertFalse(assertInstanceOf(initializer.getNode(), LazyParseableElement.class).isParsed());

    PsiCodeBlock nestedBlock = ((PsiBlockStatement)initializer.getStatements()[0]).getCodeBlock();
    assertTrue(assertInstanceOf(initializer.getNode(), LazyParseableElement.class).isParsed());
    assertFalse(assertInstanceOf(nestedBlock.getNode(), LazyParseableElement.class).isParsed());
  }

  public void testTypeCanonicalText() {
    PsiType type = JavaPsiFacade.getElementFactory(getProject()).createTypeFromText("some .unknown. Foo<? extends String>", null);
    assertEquals("some.unknown.Foo<? extends String>", type.getCanonicalText());
  }

}
