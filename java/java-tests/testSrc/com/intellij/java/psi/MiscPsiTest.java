// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightTypeParameterBuilder;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.tree.LazyParseableElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ref.GCWatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SkipSlowTestLocally
@TestApplication
public class MiscPsiTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private static void runTest(@NotNull LightJavaCodeInsightTestFixture fixture, @NotNull ThrowableRunnable<Throwable> testRunnable)
    throws Throwable {
    WriteCommandAction.writeCommandAction(fixture.getProject()).run(testRunnable);
  }

  @Test
  public void testCopyTextFile() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      String text = "1234567890";
      PsiFile file = myFixture.addFileToProject("Test.txt", text);

      VirtualFile vDir = myFixture.getTempDirFixture().findOrCreateDir("dir");

      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      assertInstanceOf(PsiPlainTextFile.class, file);
      PsiDirectory dir = myFixture.getPsiManager().findDirectory(vDir);
      PsiFile fileCopy = (PsiFile)file.copy();
      fileCopy = (PsiFile) fileCopy.setName("NewTest.txt");
      PsiFile newFile = (PsiFile)dir.add(fileCopy);
      assertInstanceOf(PsiPlainTextFile.class, newFile);

      assertEquals(text, LoadTextUtil.loadText(newFile.getVirtualFile()).toString());
      assertEquals(newFile.getVirtualFile().getModificationStamp(), newFile.getViewProvider().getModificationStamp());
      Document document = PsiDocumentManager.getInstance(myFixture.getProject()).getDocument(newFile);
      assertEquals(newFile.getVirtualFile().getModificationStamp(), document.getModificationStamp());
    });
  }

  @Test
  public void testCopyBinaryFile() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      VirtualFile vFile = myFixture.addFileToProject("Test.xxx", "").getVirtualFile();
      final byte[] bytes = {12,34,56,78,90,45,83,0x22,(byte)0xff, (byte)0xff, (byte)0xff, (byte)0xee};
      vFile.setBinaryContent(bytes);

      VirtualFile vDir = myFixture.getTempDirFixture().findOrCreateDir("dir");

      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      PsiFile file = myFixture.getPsiManager().findFile(vFile);
      assertInstanceOf(PsiBinaryFile.class, file);
      PsiDirectory dir = myFixture.getPsiManager().findDirectory(vDir);

      PsiFile fileCopy = (PsiFile)file.copy();
      fileCopy = (PsiFile) fileCopy.setName("NewTest.xxx");
      PsiFile newFile = (PsiFile)dir.add(fileCopy);
      if (!(newFile instanceof PsiBinaryFile)) {
        System.err.println(newFile.getVirtualFile().getFileType());
        System.err.println(newFile.getFileType());
        System.err.println(newFile.getText());
      }
      assertInstanceOf(PsiBinaryFile.class, newFile);

      assertArrayEquals(bytes, newFile.getVirtualFile().contentsToByteArray());
    });
  }

  @Test
  public void testCopyBinaryToTextFile() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      String text = "1234567890";
      VirtualFile vFile = myFixture.addFileToProject("Test.xxx", text).getVirtualFile();

      VirtualFile vDir = myFixture.getTempDirFixture().findOrCreateDir("dir");

      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      PsiFile file = myFixture.getPsiManager().findFile(vFile);
      PsiDirectory dir = myFixture.getPsiManager().findDirectory(vDir);
      PsiFile fileCopy = (PsiFile)file.copy();
      fileCopy = (PsiFile) fileCopy.setName("NewTest.txt");
      PsiFile newFile = (PsiFile)dir.add(fileCopy);
      assertInstanceOf(PsiPlainTextFile.class, newFile);

      assertEquals(text, VfsUtilCore.loadText(newFile.getVirtualFile()));
      assertEquals(newFile.getVirtualFile().getModificationStamp(), newFile.getViewProvider().getModificationStamp());
      assertFalse(FileDocumentManager.getInstance().isFileModified(newFile.getVirtualFile()));
    });
  }

  @Test
  public void testSCR4212() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      String text = "class A{{ return(Object)new String(); }}";
      VirtualFile vFile = myFixture.addFileToProject("Test.java", text).getVirtualFile();

      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      PsiJavaFile file = (PsiJavaFile)myFixture.getPsiManager().findFile(vFile);
      PsiClass aClass = file.getClasses()[0];
      PsiClassInitializer initializer = aClass.getInitializers()[0];
      PsiStatement statement = initializer.getBody().getStatements()[0];
      PsiTypeCastExpression typeCast = (PsiTypeCastExpression)((PsiReturnStatement)statement).getReturnValue();
      typeCast.replace(typeCast.getOperand());

      String textAfter = file.getText();
      String expectedText = "class A{{ return new String(); }}";
      assertEquals(expectedText, textAfter);
    });
  }

  @Test
  public void testDeleteFieldInMultipleDeclarations() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      final PsiClass aClass = getJavaFacade().getElementFactory().createClassFromText("public int i, j;", null);

      final PsiField aField = aClass.getFields()[0];
      WriteCommandAction.runWriteCommandAction(null, () -> aField.delete());

      assertEquals("public int j;", aClass.getFields()[0].getText());
    });
  }

  @Test
  public void testSCR5929() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      String text = "class A{ /** @see a.B */ }";
      VirtualFile vFileA = myFixture.addFileToProject("A.java", text).getVirtualFile();

      VirtualFile dir = myFixture.getTempDirFixture().findOrCreateDir("a");
      VirtualFile vFileB = dir.createChildData(null, "B.java");
      text = "class B{}";
      VfsUtil.saveText(vFileB, text);

      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      PsiFile fileA = myFixture.getPsiManager().findFile(vFileA);
      PsiJavaFile fileACopy = (PsiJavaFile)fileA.copy();
      PsiClass aClass = fileACopy.getClasses()[0];
      aClass.setName("A2");
      fileA.getContainingDirectory().add(fileACopy);
    });
  }

  @Test
  public void testCopyClass() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      String text = "package aaa; class A{}";
      VirtualFile vFile = myFixture.addFileToProject("A.java", text).getVirtualFile();

      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      PsiFile file = myFixture.getPsiManager().findFile(vFile);
      PsiJavaFile fileACopy = (PsiJavaFile)file.copy();
      PsiClass aClass = fileACopy.getClasses()[0];
      aClass.setName("ANew");
      PsiFile newFile = (PsiFile)file.getContainingDirectory().add(fileACopy);
      Document document = PsiDocumentManager.getInstance(myFixture.getProject()).getDocument(newFile);
      FileDocumentManager.getInstance().saveDocument(document);
      assertEquals(newFile.getVirtualFile().getModificationStamp(), newFile.getViewProvider().getModificationStamp());
      assertFalse(FileDocumentManager.getInstance().isFileModified(newFile.getVirtualFile()));
    });
  }

  @Test
  public void testSCR15954() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("A.java", "class A{\nA(){}\n}");

      PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(myFixture.getProject());
      Document document = psiDocumentManager.getDocument(file);
      document.insertString(document.getTextLength(), " "); // insert a trailing space to strip
      psiDocumentManager.commitAllDocuments();

      PsiClass aClass = file.getClasses()[0];
      aClass.setName("B");
    });
  }

  @Test
  public void testDeleteAnnotationAttribute() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      final PsiAnnotation annotation = getJavaFacade().getElementFactory().createAnnotationFromText("@A(b,c)", null);
      final PsiNameValuePair secondAttribute = annotation.getParameterList().getAttributes()[1];
      ApplicationManager.getApplication().runWriteAction(() -> secondAttribute.delete());

      assertEquals("@A(b )", annotation.getText());
    });
  }

  @Test
  public void testDeleteAnnotationArrayInitializerElement() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      final PsiAnnotation annotation = getJavaFacade().getElementFactory().createAnnotationFromText("@A({b,c})", null);
      final PsiNameValuePair firstAttribute = annotation.getParameterList().getAttributes()[0];
      assertTrue(firstAttribute.getValue() instanceof PsiArrayInitializerMemberValue);
      final PsiAnnotationMemberValue firstInitializer = ((PsiArrayInitializerMemberValue)firstAttribute.getValue()).getInitializers()[0];
      ApplicationManager.getApplication().runWriteAction(() -> firstInitializer.delete());

      assertEquals("@A({ c})", annotation.getText());
    });
  }

  private JavaPsiFacade getJavaFacade() {
    return JavaPsiFacade.getInstance(codeInsightFixture.get().getProject());
  }

  @Test
  public void testJavaLangObjectSuperMethod() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      final PsiClass aClass =
        getJavaFacade().getElementFactory().createClassFromText("public String toString() {return null;}", null);
      final PsiMethod method = aClass.getMethods()[0];
      final PsiMethod[] superMethods = method.findSuperMethods();
      assertEquals(1, superMethods.length);
      assertEquals(CommonClassNames.JAVA_LANG_OBJECT, superMethods[0].getContainingClass().getQualifiedName());
    });
  }

  @Test
  public void testImportOnDemand() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      final PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(myFixture.getProject()).createFileFromText("D.java",
                                                                                                                        "import java.util.Map.Entry");
      PsiImportStatement importStatement = file.getImportList().getImportStatements()[0];
      assertFalse(importStatement.isOnDemand());
    });
  }

  @Test
  public void testDocCommentPrecededByLineComment() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      final PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(myFixture.getProject()).createFileFromText("D.java",
                                                                                                        """
                                                                                                          ////////////////////////////////////////
                                                                                                          /** */
                                                                                                          /////////////////////////////////////////////////
                                                                                                          class Usage {
                                                                                                          }""");
      final PsiClass psiClass = file.getClasses()[0];
      assertNotNull(psiClass.getDocComment());
    });
  }

  @Test
  public void testTopLevelEnumIsNotStatic() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      final JavaPsiFacade facade = getJavaFacade();
      final LanguageLevel prevLanguageLevel = IdeaTestUtil.setProjectLanguageLevel(facade.getProject(), LanguageLevel.JDK_1_5);
      final PsiClass aClass;
      try {
        aClass = JavaPsiFacade.getElementFactory(myFixture.getProject()).createEnum("E");
      }
      finally {
        IdeaTestUtil.setProjectLanguageLevel(facade.getProject(), prevLanguageLevel);
      }
      assertTrue(aClass.isEnum());
      assertFalse(aClass.hasModifierProperty(PsiModifier.STATIC));
    });
  }

  @Test
  public void testDoNotExpandNestedChameleons() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("a.java", "class A {{{}}}");
      //noinspection ResultOfMethodCallIgnored
      file.getNode(); // load tree

      PsiCodeBlock initializer = file.getClasses()[0].getInitializers()[0].getBody();
      assertFalse(assertInstanceOf(LazyParseableElement.class, initializer.getNode()).isParsed());

      PsiCodeBlock nestedBlock = ((PsiBlockStatement)initializer.getStatements()[0]).getCodeBlock();
      assertTrue(assertInstanceOf(LazyParseableElement.class, initializer.getNode()).isParsed());
      assertFalse(assertInstanceOf(LazyParseableElement.class, nestedBlock.getNode()).isParsed());
    });
  }

  @Test
  public void testTypeCanonicalText() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      PsiType type = JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("some .unknown. Foo<? extends String>", null);
      assertEquals("some.unknown.Foo<? extends String>", type.getCanonicalText());
    });
  }

  @Test
  public void testNoPsiModificationsInUncommittedDocument() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      final PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("a.java", "class A{}");
      Document document = file.getViewProvider().getDocument();
      document.insertString(0, " ");

      PsiClass psiClass = file.getClasses()[0];
      try {
        psiClass.addBefore(PsiParserFacade.getInstance(myFixture.getProject()).createWhiteSpaceFromText(" "), psiClass.getLBrace());
        fail();
      }
      catch (IllegalStateException e) {
        assertEquals("Attempt to modify PSI for non-committed Document!", e.getMessage());
      }
      assertEquals("class A{}", psiClass.getText());
      assertEquals(" class A{}", document.getText());
    });
  }

  @Test
  public void testASTBecomesInvalidOnExternalChange() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      final String text = "class A{}";
      final PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("a.java", text);
      PsiElement leaf = file.findElementAt(5);

      GCWatcher.tracking(file.getViewProvider().getDocument()).ensureCollected();
      assertNull(PsiDocumentManager.getInstance(myFixture.getProject()).getCachedDocument(file));

      WriteCommandAction.writeCommandAction(myFixture.getProject()).run(() -> VfsUtil.saveText(file.getVirtualFile(), text + "   "));

      assertTrue(file.isValid());
      assertFalse(leaf.isValid());
      assertNotSame(leaf, file.findElementAt(5));
    });
  }

  @Test
  public void testPsiModificationsWithNoDocument() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      final PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("a.java", "class A{}");

      PsiClass aClass = file.getClasses()[0];
      assertNotNull(aClass.getNode());
      assertNotNull(PsiDocumentManager.getInstance(myFixture.getProject()).getCachedDocument(file));

      GCWatcher.tracking(PsiDocumentManager.getInstance(myFixture.getProject()).getCachedDocument(file)).ensureCollected();
      assertNull(PsiDocumentManager.getInstance(myFixture.getProject()).getCachedDocument(file));

      aClass.add(JavaPsiFacade.getElementFactory(myFixture.getProject()).createMethodFromText("void foo(){}", null));
      assertNotNull(PsiDocumentManager.getInstance(myFixture.getProject()).getCachedDocument(file));

      PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();

      assertTrue(file.getText().contains("foo() {\n"), file.getText());

    });
  }

  @Test
  public void testAfterPsiModificationPsiDocumentStampsAreSame() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("a.java", "class A{public static void foo() { }}");

      PsiClass aClass = file.getClasses()[0];
      GCWatcher.tracking(aClass.getNode()).ensureCollected();

      PsiKeyword kw = assertInstanceOf(PsiKeyword.class, aClass.getMethods()[0].getModifierList().getFirstChild());
      kw.delete();

      Document document = PsiDocumentManager.getInstance(myFixture.getProject()).getDocument(file);
      assertNotNull(document);
      assertTrue(document.getModificationStamp() != file.getVirtualFile().getModificationStamp());
      assertEquals(document.getModificationStamp(), file.getViewProvider().getModificationStamp());
      FileDocumentManager.getInstance().saveDocument(document);

      assertEquals(file.getText(), LoadTextUtil.loadText(file.getVirtualFile()).toString());
    });
  }

  @Test
  public void testPsiClassMethodsCantBeModified() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      PsiClass psiClass = myFixture.addClass("class Foo { " +
                                             "Foo() {} " +
                                             "void bar() {} " +
                                             "void goo() {} " +
                                             "void goo42() {} " +
                                             "}");
      PsiMethod[] golden = psiClass.getMethods().clone();

      PsiMethod[] mutated = psiClass.getMethods();
      mutated[0] = mutated[1];
      mutated[2] = null;

      assertArrayEquals(golden, psiClass.getMethods());
    });
  }

  @Test
  public void testRecoverAfterPsiDocSyncError() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile vFile = myFixture.addFileToProject("a.java", "class A{}").getVirtualFile();
    runTest(myFixture, () -> {
      try {
        myFixture.findClass("A").replace(JavaPsiFacade.getElementFactory(myFixture.getProject()).createClassFromText("class B {\r\n}", null));
        fail("Should fail");
      }
      catch (Throwable e) {
        assertTrue(e.getMessage().contains("Wrong line separators"), e.getMessage());
      }

      assertEquals("class A{}", myFixture.getPsiManager().findFile(vFile).getText());

      VfsUtil.saveText(vFile, "class C {}");
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();

      PsiClass c = myFixture.findClass("C");
      assertNotNull(c);
      assertEquals(vFile, c.getContainingFile().getVirtualFile());
    });
  }

  @Test
  public void testResolveCacheInvalidatedOnFilePropertyChange() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile foo = myFixture.addFileToProject("Foo.java", "class Foo {}").getVirtualFile();
    PsiClass bar = myFixture.addClass("class Bar extends Foo {}");
    runTest(myFixture, () -> {
      assertEquals("Foo", bar.getSuperClass().getName());
      PsiUtilCore.ensureValid(bar.getSuperClass());

      PushedFilePropertiesUpdater.getInstance(myFixture.getProject()).filePropertiesChanged(foo, Conditions.alwaysTrue());

      assertEquals("Foo", bar.getSuperClass().getName());
      PsiUtilCore.ensureValid(bar.getSuperClass());
    });
  }

  @Test
  public void testLoadingNodeDuringCommitWithoutReparse_Stress(@TestDisposable Disposable disposable) throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ApplicationManagerEx.runInStressTest(true, () -> runTest(myFixture, () -> {
      // it's a stress test to disable test checks that load file content prematurely (AbstractFileViewProvider.checkLengthConsistency)
      PsiFile file = myFixture.addFileToProject("a.java", "class Foo {}");

      myFixture.getPsiManager().addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
        @Override
        public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
          PsiElement parent = event.getParent();
          if (parent == file) {
            assertNotNull(file.getNode());
          }
        }
      }, disposable);
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
        Document document = file.getViewProvider().getDocument();
        document.insertString(0, " ");
        PsiDocumentManager.getInstance(myFixture.getProject()).commitDocument(document);
      });

      assertEquals(" class Foo {}", file.getText());
    }));
  }

  @Test
  public void testGenericLightMethodBuilderEquivalence() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      Factory<PsiMethod> createMethod = () -> {
        LightMethodBuilder method = new LightMethodBuilder(myFixture.getPsiManager(), "foo");
        LightTypeParameterBuilder typeParam = new LightTypeParameterBuilder("T", method, 0);
        method.addTypeParameter(typeParam);
        PsiClassType tType = JavaPsiFacade.getElementFactory(myFixture.getProject()).createType(typeParam);
        method.setMethodReturnType(tType);
        method.addParameter("p", tType);
        return method;
      };

      assertTrue(myFixture.getPsiManager().areElementsEquivalent(createMethod.create(), createMethod.create()));
    });
  }

  @Test
  public void testFoundPackageIsValid() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runTest(myFixture, () -> {
      assertTrue(myFixture.findPackage("org.apache").isValid());
    });
  }
}
