// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.CreateSubclassAction;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.PathUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class CreateSubclassTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/createSubclass/");

  @Test
  public void testGenerics() {
    doTest();
  }

  @Test
  public void testImports() {
    doTest();
  }

  @Test
  public void testInnerClassImplement() {
    doTestIntention("Implement abstract class");
  }

  @Test
  public void testInnerClass() {
    doTestIntention("Implement interface");
  }

  @Test
  public void testPublicNonStaticInnerClass() {
    doTestIntention("Create subclass");
  }

  @Test
  public void testLocalClass() {
    doTestIntention("Create subclass");
  }

  @Test
  public void testSealed() {
    doTest();
  }

  @Test
  public void testSealedWithSameFileInheritors() {
    doTestSameFileClass();
  }

  @Test
  public void testScratch() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      VirtualFile scratch =
        ScratchRootType.getInstance()
          .createScratchFile(myFixture.getProject(), PathUtil.makeFileName("scratch", "java"), JavaLanguage.INSTANCE,
                             "class Scrat<caret>ch { }", ScratchFileService.Option.create_if_missing);
      assertNotNull(scratch);
      myFixture.configureFromExistingVirtualFile(scratch);
      IntentionAction intention = myFixture.findSingleIntention("Create subclass");
      assertNotNull(intention);
      intention.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      myFixture.checkResult("""
                              class Scratch { }

                              class <caret>ScratchImpl extends Scratch {
                              }""");
    });
  }

  private void doTestIntention(String hint) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByFile(myFixture.getTestName(false) + ".java");
      IntentionAction action = myFixture.findSingleIntention(hint);
      myFixture.launchAction(action);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResultByFile(myFixture.getTestName(false) + ".after.java");
    });
  }

  private void doTestSameFileClass() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      PsiClass superClass = myFixture.findClass("Superclass");
      ApplicationManager.getApplication().invokeLater(
        () -> CreateSubclassAction.createSameFileClass("Subclass", superClass));
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    });
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      PsiDirectory root = PsiManager.getInstance(myFixture.getProject()).findDirectory(myFixture.getTempDirFixture().findOrCreateDir(""));
      PsiClass superClass = myFixture.findClass("Superclass");
      ApplicationManager.getApplication().invokeLater(
        () -> CreateSubclassAction.createSubclass(superClass, root, "Subclass"));
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    });
  }
}
