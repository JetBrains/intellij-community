// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Objects;

import static com.intellij.java.codeInsight.daemon.JdkDaemonAnalyzerFixture.jdkDaemonAnalyzerFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class UnusedSymbolLocalTest {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/unusedDecls";

  private final TestFixture<JdkDaemonAnalyzerFixture> daemonFixture =
    jdkDaemonAnalyzerFixture(JavaTestUtil.getJavaTestDataPath(), () -> IdeaTestUtil.getMockJdk18());

  @BeforeEach
  void setUp() {
    daemonFixture.get().enableInspectionTool(new UnusedDeclarationInspection(true));
  }

  @Test
  public void testInnerClass() { doTest(); }
  @Test
  public void testInnerClassWithMainMethod() { doTest(); }
  @Test
  public void testInnerUsesSelf() { doTest(); }
  @Test
  public void testLocalClass() { doTest(); }
  @Test
  public void testRepeatableAnnotation() { doTest(); }
  @Test
  public void testPrivateConstructor() { doTest(); }
  @Test
  public void testEnumValueOf() { doTest(); }
  @Test
  public void testJustIncremented() { doTest(); }
  @Test
  public void testImplicitClassInstanceMainWithoutParams() { IdeaTestUtil.withLevel(daemonFixture.get().getModule(), LanguageLevel.JDK_21_PREVIEW, () -> doTest()); }
  @Test
  public void testUnaryExpressionInSwitch() { IdeaTestUtil.withLevel(daemonFixture.get().getModule(), LanguageLevel.JDK_21_PREVIEW, () -> doTest()); }

  @Test
  public void testImplicitReadsWrites() {
    ImplicitUsageProvider.EP_NAME.getPoint().registerExtension(new ImplicitUsageProvider() {
      @Override
      public boolean isImplicitUsage(@NotNull PsiElement element) {
        return isImplicitWrite(element) || isImplicitRead(element);
      }

      @Override
      public boolean isImplicitRead(@NotNull PsiElement element) {
        return element instanceof PsiField field && field.getName().contains("Read");
      }

      @Override
      public boolean isImplicitWrite(@NotNull PsiElement element) {
        return element instanceof PsiField field && field.getName().contains("Written");
      }
    }, daemonFixture.get().getTestRootDisposable());

    doTest();
  }

  @Test
  public void testChangeInsideCodeBlock() {
    doTest();
    JdkDaemonAnalyzerFixture fixture = daemonFixture.get();
    final Document document = fixture.getEditor().getDocument();
    Collection<HighlightInfo> collection = fixture.doHighlighting(HighlightSeverity.WARNING);
    assertEquals(2, collection.size());

    WriteCommandAction.runWriteCommandAction(null, () -> document.insertString(fixture.getEditor().getCaretModel().getOffset(), "//"));
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(fixture.getProject()).commitDocument(document));

    Collection<HighlightInfo> infos = fixture.doHighlighting(HighlightSeverity.WARNING);
    assertEquals(3, infos.size());
  }

  @Test
  public void testIgnoreUsagesFromTests() {
    UnusedDeclarationInspection inspection = new UnusedDeclarationInspection(true);
    inspection.setTestEntryPoints(false);
    daemonFixture.get().enableInspectionTool(inspection);
    createTestSourceFile("BTest.java", """
      public class BTest {
      
          public void foo()  {
              B b = new B();
              b.foo();
      
          }
      }""");
    doTest();
  }

  @Test
  public void testUsagesFromTests() {
    createTestSourceFile("IconoclastTest.java", """
      public class IconoclastTest {
      
          public void foo()  {
              Iconoclast.pernicious();
          }
      }""");
    doTest();
  }

  private void createTestSourceFile(String fileName, String text) {
    JdkDaemonAnalyzerFixture fixture = daemonFixture.get();
    Module myModule = fixture.getModule();
    try {
      final @NotNull VirtualFile vDir = fixture.createTempVirtualDir();
      VirtualFile virtualFile = WriteAction.computeAndWait(() -> {
        if (!ModuleRootManager.getInstance(myModule).getFileIndex().isInSourceContent(vDir)) {
          PsiTestUtil.addSourceContentToRoots(myModule, vDir, true);
        }

        VirtualFile vFile = Objects.requireNonNull(vDir.createChildData(vDir, fileName));
        VfsUtil.saveText(vFile, text);
        return vFile;
      });
      IndexingTestUtil.waitUntilIndexesAreReady(fixture.getProject());
      Objects.requireNonNull(ReadAction.computeBlocking(() -> PsiManager.getInstance(fixture.getProject()).findFile(virtualFile)));
      fixture.allowTreeAccessForAllFiles();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void doTest() {
    JdkDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(BASE_PATH + "/" + fixture.getTestName(false) + ".java", true, false);
  }
}
