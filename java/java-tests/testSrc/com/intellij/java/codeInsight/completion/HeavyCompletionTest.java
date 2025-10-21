// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.statistics.impl.StatisticsManagerImpl;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

public class HeavyCompletionTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testPackagePrefix() {
    myFixture.configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    ApplicationManager.getApplication().runWriteAction(() -> {
      final ModifiableRootModel model = ModuleRootManager.getInstance(myFixture.getModule()).getModifiableModel();
      model.getContentEntries()[0].getSourceFolders()[0].setPackagePrefix("foo.bar.goo");
      model.commit();
    });

    myFixture.completeBasic();
    myFixture.checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
    assertTrue(JavaPsiFacade.getInstance(getProject()).findPackage("foo").isValid());
    assertTrue(JavaPsiFacade.getInstance(getProject()).findPackage("foo.bar").isValid());
    assertTrue(JavaPsiFacade.getInstance(getProject()).findPackage("foo.bar.goo").isValid());
  }

  @NeedsIndex.Full
  public void testPreferTestCases() {
    myFixture.configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    ApplicationManager.getApplication().runWriteAction(() -> {
      final ModifiableRootModel model = ModuleRootManager.getInstance(myFixture.getModule()).getModifiableModel();
      ContentEntry contentEntry = model.getContentEntries()[0];
      SourceFolder sourceFolder = contentEntry.getSourceFolders()[0];
      VirtualFile file = sourceFolder.getFile();
      contentEntry.removeSourceFolder(sourceFolder);
      contentEntry.addSourceFolder(file, true);
      model.commit();
    });

    myFixture.addClass("package foo; public class SomeTestCase {}");
    myFixture.addClass("package bar; public class SomeTestec {}");
    myFixture.addClass("package goo; public class SomeAnchor {}");

    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "SomeTestCase", "SomeAnchor", "SomeTestec");
  }

  @NeedsIndex.Full
  public void testAllClassesWhenNothingIsFound() {
    myFixture.addClass("package foo.bar; public class AxBxCxDxEx {}");

    myFixture.configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    myFixture.completeBasic();
    myFixture.type("\n");
    myFixture.checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
  }

  @NeedsIndex.Full
  public void testAllClassesOnSecondBasicCompletion() {
    myFixture.addClass("package foo.bar; public class AxBxCxDxEx {}");

    myFixture.configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC, 2);
    assertEquals(List.of("AyByCyDyEy", "AxBxCxDxEx"), myFixture.getLookupElementStrings());
  }

  public void testMapsInvalidation() {
    JavaAutoPopupTest.registerCompletionContributor(CacheVerifyingContributor.class, myFixture.getTestRootDisposable(), LoadingOrder.FIRST);
    myFixture.configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    assertInstanceOf(myFixture.getFile().getVirtualFile().getFileSystem(),
                     LocalFileSystem.class);// otherwise, the completion copy won't be preserved which is critical here
    myFixture.completeBasic();
    assertOrderedEquals(myFixture.getLookupElementStrings(), "getAaa", "getBbb");
    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getCaretModel().getOffset() + 2);
    assertNull(myFixture.completeBasic());
  }

  @NeedsIndex.Full
  public void testQualifyInaccessibleClassName() throws Exception {
    PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "second", myFixture.getTempDirFixture().findOrCreateDir("second"));
    myFixture.addFileToProject("second/foo/bar/AxBxCxDxEx.java", "package foo.bar; class AxBxCxDxEx {}");

    myFixture.configureByText("a.java", "class Main { ABCDE<caret> }");
    myFixture.complete(CompletionType.BASIC, 3);
    myFixture.checkResult("class Main { foo.bar.AxBxCxDxEx<caret> }");
  }

  public void testNoJavaStructureModificationOnSecondInvocation() {
    myFixture.configureByText("a.java", "class Foo { Xxxxx<caret> }");
    long oldCount = PsiManager.getInstance(getProject()).getModificationTracker().getModificationCount();
    assertEquals(0, myFixture.completeBasic().length);
    assertEquals(0, myFixture.completeBasic().length);
    assertEquals(oldCount, PsiManager.getInstance(getProject()).getModificationTracker().getModificationCount());
  }

  public void testNoJavaStructureModificationOnSecondInvocationAfterTyping() {
    myFixture.configureByText("a.java", "class Foo { Xxxxx<caret> }");

    PsiModificationTracker tracker = PsiManager.getInstance(getProject()).getModificationTracker();
    long oldCount = tracker.getModificationCount();
    assertEquals(0, myFixture.completeBasic().length);
    assertEquals(oldCount, tracker.getModificationCount());

    myFixture.type("x");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertTrue(oldCount != tracker.getModificationCount());
    oldCount = tracker.getModificationCount();

    assertEquals(0, myFixture.completeBasic().length);
    assertEquals(0, myFixture.completeBasic().length);
    assertEquals(oldCount, tracker.getModificationCount());
  }

  @NeedsIndex.Full
  public void testForbiddenApiVariants() {
    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_1_4);
    myFixture.addClass("""
                         package java.nio.channels;
                         public class SocketChannel {
                           public SocketChannel shutdownInput() {}
                           public boolean isConnected();
                         }""");
    myFixture.addClass("package java.nio.channels; public class AsynchronousServerSocketChannel { }");

    myFixture.configureByText("a.java", "class Foo {{ new SocketChanne<caret>x }}");
    myFixture.completeBasic();
    LookupElementPresentation p = NormalCompletionTestCase.renderElement(myFixture.getLookup().getItems().get(0));
    assertEquals("SocketChannel", p.getItemText());
    assertEquals(JBColor.foreground(), p.getItemTextForeground());

    p = NormalCompletionTestCase.renderElement(
      ContainerUtil.find(myFixture.getLookup().getItems(), item -> item.getLookupString().equals("AsynchronousServerSocketChannel")));
    assertEquals(JBColor.RED, p.getItemTextForeground());

    myFixture.type("\n.s");
    myFixture.completeBasic();
    p = NormalCompletionTestCase.renderElement(myFixture.getLookup().getItems().get(0));
    assertEquals("shutdownInput", p.getItemText());
    assertEquals(JBColor.RED, p.getItemTextForeground());

    p = NormalCompletionTestCase.renderElement(
      ContainerUtil.find(myFixture.getLookup().getItems(), item -> item.getLookupString().equals("isConnected")));
    assertEquals(JBColor.foreground(), p.getItemTextForeground());
  }

  // TODO (IJPL-426): DUMB_RUNTIME_ONLY_INDEX means "entities available before the test has changed indexing mode"
  //  adding a library in the middle of the test will not cause indexing of this library
  //  This is a bug in DUMB_RUNTIME_ONLY_INDEX implementation, not in the test
  // @NeedsIndex.ForStandardLibrary
  @NeedsIndex.Full
  public void testSeeminglyScrambledSubclass() {
    PsiTestUtil.addLibrary(getModule(), JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/normal/seemsScrambled.jar");
    myFixture.configureByText("a.java", """
      import test.Books;

      class Foo {{ Books.Test.v<caret> }}
      """);
    myFixture.completeBasic();
    myFixture.checkResult("""
                            import test.Books;

                            class Foo {{ Books.Test.v1<caret> }}
                            """);
  }

  @NeedsIndex.Full
  public void testDifferentJdksInDifferentModules() throws IOException {
    ((StatisticsManagerImpl)StatisticsManager.getInstance()).enableStatistics(myFixture.getTestRootDisposable());

    Module anotherModule =
      PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "another", myFixture.getTempDirFixture().findOrCreateDir("another"));
    ModuleRootModificationUtil.setModuleSdk(anotherModule, IdeaTestUtil.getMockJdk17());
    Sdk jdk14 = IdeaTestUtil.getMockJdk14();
    WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().addJdk(jdk14, getTestRootDisposable()));
    ModuleRootModificationUtil.setModuleSdk(myFixture.getModule(), jdk14);
    ModuleRootModificationUtil.addDependency(myFixture.getModule(), anotherModule);

    myFixture.addFileToProject("another/Decl.java", """
      public class Decl {
      public static void method(Runnable r) {}
      }
      """);
    myFixture.configureByText("a.java", "class Usage {{ Decl.method(new <caret>); }}");
    myFixture.complete(CompletionType.SMART);
    myFixture.assertPreferredCompletionItems(0, "Runnable", "Thread");
    myFixture.type("\n");

    myFixture.configureByText("b.java", "class Usage {{ Decl.method(new <caret>); }}");
    myFixture.complete(CompletionType.SMART);
    myFixture.assertPreferredCompletionItems(0, "Runnable", "Thread");
    myFixture.type("\n");
  }

  @NeedsIndex.Full
  public void testSealedClassInJavaModule() {
    myFixture.addFileToProject("module-info.java", "module Module1 {}");
    myFixture.addFileToProject("bar/Child.java", "package bar;\nimport foo.*;\npublic final class Child implements Foo {}");
    myFixture.configureByText("foo/Foo.java", "package foo;\npublic sealed interface Foo permits <caret> {}");
    myFixture.complete(CompletionType.BASIC);
    myFixture.type("\n");
    myFixture.checkResult("package foo;\n\nimport bar.Child;\n\npublic sealed interface Foo permits Child {}");
  }

  public static class CacheVerifyingContributor extends CompletionContributor {
    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
      PsiClass psiClass = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiClass.class);
      for (CandidateInfo ci : OverrideImplementExploreUtil.getMethodsToOverrideImplement(psiClass, true)) {
        assertTrue(ci.getElement().isValid());
      }

      for (CandidateInfo ci : OverrideImplementExploreUtil.getMethodsToOverrideImplement(psiClass, false)) {
        assertTrue(ci.getElement().isValid());
      }
    }
  }
}
