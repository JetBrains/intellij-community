// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.analysis.PackagesScopesProvider;
import com.intellij.application.options.colors.ScopeAttributesUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.ProblemHighlightFilter;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PatternPackageSet;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.io.File;
import java.util.Collection;
import java.util.concurrent.ExecutionException;

/**
 * This class intended for "heavy-loaded" tests only, e.g. those need to setup separate project directory structure to run.
 * For "lightweight" tests use LightAdvHighlightingTest.
 */
public class AdvHighlightingTest extends DaemonAnalyzerTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting";

  @Override
  protected Sdk getTestProjectJdk() {
    setLanguageLevel(LanguageLevel.JDK_1_4);
    return IdeaTestUtil.getMockJdk14();
  }

  private EditorColorsScheme cloneColorSchema() {
    EditorColorsManager manager = EditorColorsManager.getInstance();
    EditorColorsScheme globalScheme = manager.getGlobalScheme();
    EditorColorsScheme scheme = (EditorColorsScheme)globalScheme.clone();
    manager.addColorScheme(scheme);
    manager.setGlobalScheme(scheme);
    Disposer.register(getTestRootDisposable(), () -> manager.setGlobalScheme(globalScheme));
    return scheme;
  }

  public void testScopeBased() {
    NamedScope xScope = new NamedScope("xxx", new PatternPackageSet("x..*", PatternPackageSet.Scope.SOURCE, null));
    NamedScope utilScope = new NamedScope("util", new PatternPackageSet("java.util.*", PatternPackageSet.Scope.LIBRARY, null));
    NamedScopeManager scopeManager = NamedScopeManager.getInstance(getProject());
    scopeManager.addScope(xScope);
    scopeManager.addScope(utilScope);

    EditorColorsScheme scheme = cloneColorSchema();
    TextAttributesKey xKey = ScopeAttributesUtil.getScopeTextAttributeKey(xScope.getScopeId());
    TextAttributes xAttributes = new TextAttributes(Color.cyan, Color.darkGray, Color.blue, EffectType.BOXED, Font.ITALIC);
    scheme.setAttributes(xKey, xAttributes);

    TextAttributesKey utilKey = ScopeAttributesUtil.getScopeTextAttributeKey(utilScope.getScopeId());
    TextAttributes utilAttributes = new TextAttributes(Color.gray, Color.magenta, Color.orange, EffectType.STRIKEOUT, Font.BOLD);
    scheme.setAttributes(utilKey, utilAttributes);

    try {
      testFile(BASE_PATH + "/scopeBased/x/X.java").projectRoot(BASE_PATH + "/scopeBased").checkSymbolNames().test();
    }
    finally {
      scopeManager.removeAllSets();
    }
  }

  public void testSharedScopeBased() {
    NamedScope xScope = new NamedScope("xxx", new PatternPackageSet("x..*", PatternPackageSet.Scope.ANY, null));
    NamedScope utilScope = new NamedScope("util", new PatternPackageSet("java.util.*", PatternPackageSet.Scope.LIBRARY, null));
    NamedScopesHolder scopeManager = DependencyValidationManager.getInstance(getProject());
    scopeManager.addScope(xScope);
    scopeManager.addScope(utilScope);

    EditorColorsScheme scheme = cloneColorSchema();
    TextAttributesKey xKey = ScopeAttributesUtil.getScopeTextAttributeKey(xScope.getScopeId());
    TextAttributes xAttributes = new TextAttributes(Color.cyan, Color.darkGray, Color.blue, null, Font.ITALIC);
    scheme.setAttributes(xKey, xAttributes);

    TextAttributesKey utilKey = ScopeAttributesUtil.getScopeTextAttributeKey(utilScope.getScopeId());
    TextAttributes utilAttributes = new TextAttributes(Color.gray, Color.magenta, Color.orange, EffectType.STRIKEOUT, Font.BOLD);
    scheme.setAttributes(utilKey, utilAttributes);

    NamedScope projectScope = PackagesScopesProvider.getInstance(myProject).getProjectProductionScope();
    TextAttributesKey projectKey = ScopeAttributesUtil.getScopeTextAttributeKey(projectScope.getScopeId());
    TextAttributes projectAttributes = new TextAttributes(null, null, Color.blue, EffectType.BOXED, Font.ITALIC);
    scheme.setAttributes(projectKey, projectAttributes);

    try {
      testFile(BASE_PATH + "/scopeBased/x/Shared.java").projectRoot(BASE_PATH + "/scopeBased").checkSymbolNames().test();
    }
    finally {
      scopeManager.removeAllSets();
    }
  }

  public void testVisibilityBased() {
    EditorColorsScheme scheme = cloneColorSchema();
    TextAttributesKey xKey = JavaHighlightingColors.PRIVATE_REFERENCE_ATTRIBUTES;
    TextAttributes xAttributes = new TextAttributes(null, null, Color.orange, EffectType.BOXED, Font.PLAIN);
    scheme.setAttributes(xKey, xAttributes);
    testFile(BASE_PATH + "/visibility/Simple.java").projectRoot(BASE_PATH + "/visibility").checkSymbolNames().test();
  }

  public void testMultiJDKConflict() {
    String path = PathManagerEx.getTestDataPath() + BASE_PATH + "/" + getTestName(true);
    VirtualFile root = LocalFileSystem.getInstance().findFileByIoFile(new File(path));
    assert root != null : path;
    loadAllModulesUnder(root);

    ModuleManager moduleManager = ModuleManager.getInstance(getProject());
    Module java4 = moduleManager.findModuleByName("java4");
    Module java5 = moduleManager.findModuleByName("java5");
    Sdk jdk17 = IdeaTestUtil.getMockJdk17("java 1.4");
    WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().addJdk(jdk17, getTestRootDisposable()));
    ModuleRootModificationUtil.setModuleSdk(java4, jdk17);
    Sdk jdk171 = IdeaTestUtil.getMockJdk17("java 1.5");
    WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().addJdk(jdk171, getTestRootDisposable()));
    ModuleRootModificationUtil.setModuleSdk(java5, jdk171);
    ModuleRootModificationUtil.addDependency(java5, java4);
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());

    configureByExistingFile(root.findFileByRelativePath("moduleJava5/com/Java5.java"));
    Collection<HighlightInfo> infos = highlightErrors();
    assertEmpty(infos);
  }

  public void testSameFQNClasses() {
    String path = PathManagerEx.getTestDataPath() + BASE_PATH + "/" + getTestName(true);
    VirtualFile root = LocalFileSystem.getInstance().findFileByIoFile(new File(path));
    assert root != null : path;
    loadAllModulesUnder(root);

    configureByExistingFile(root.findFileByRelativePath("client/src/BugTest.java"));
    Collection<HighlightInfo> infos = highlightErrors();
    assertEmpty(infos);
  }

  public void testSameClassesInSourceAndLib() {
    String path = PathManagerEx.getTestDataPath() + BASE_PATH + "/" + getTestName(true);
    VirtualFile root = LocalFileSystem.getInstance().findFileByIoFile(new File(path));
    assert root != null : path;
    loadAllModulesUnder(root);

    configureByExistingFile(root.findFileByRelativePath("src/ppp/SomeClass.java"));
    PsiField field = ((PsiJavaFile)myFile).getClasses()[0].findFieldByName("f", false);
    assert field != null;
    PsiClass aClass = ((PsiClassType)field.getType()).resolve();
    assert aClass != null;
    assertEquals("ppp.BadClass", aClass.getQualifiedName());
    //lies in source
    VirtualFile vFile1 = myFile.getVirtualFile();
    VirtualFile vFile2 = aClass.getContainingFile().getVirtualFile();
    assert vFile1 != null;
    assert vFile2 != null;
    assertEquals(vFile1.getParent(), vFile2.getParent());
  }

  // todo[r.sh] IDEA-91596 (probably PJCRE.resolve() should be changed to qualifier-first model)
  //public void testPackageAndClassConflict3() throws Exception {
  //  doTest(BASE_PATH + "/packageClassClash3/test/Test.java", BASE_PATH + "/packageClassClash3", false, false);
  //}

  public void testUnusedPublicMethodReferencedViaSubclass() throws Exception {
    UnusedDeclarationInspectionBase deadCodeInspection = new UnusedDeclarationInspectionBase(true);
    enableInspectionTool(deadCodeInspection);
    allowTreeAccessForAllFiles();

    doTest(BASE_PATH + "/unusedPublicMethodRefViaSubclass/x/I.java", BASE_PATH + "/unusedPublicMethodRefViaSubclass", true, false);
  }

  public void testJavaFileOutsideSourceRootsMustNotContainErrors() throws ExecutionException, InterruptedException {
    VirtualFile root = createVirtualDirectoryForContentFile();
    VirtualFile virtualFile = createChildData(root, "A.java");
    @Language("JAVA")
    String text = "class A { String f; }";
    setFileText(virtualFile, text);

    assertEquals(JavaFileType.INSTANCE, virtualFile.getFileType());
    PsiFile psiFile = getPsiManager().findFile(virtualFile);
    assertTrue(String.valueOf(psiFile), psiFile instanceof PsiJavaFile);
    assertFalse(ProblemHighlightFilter.shouldHighlightFile(psiFile));

    configureByExistingFile(virtualFile);

    TrafficLightRenderer renderer = ReadAction.nonBlocking(()->new TrafficLightRenderer(getProject(), getEditor())).submit(
      AppExecutorUtil.getAppExecutorService()).get();
    Disposer.register(getTestRootDisposable(), renderer);
    while (true) {
      TrafficLightRenderer.DaemonCodeAnalyzerStatus status = ReadAction.nonBlocking(() -> renderer.getDaemonCodeAnalyzerStatus()).submit(AppExecutorUtil.getAppExecutorService()).get();
      assertNotNull(status.reasonWhyDisabled);
      if (status.errorAnalyzingFinished) {
        break;
      }
      UIUtil.dispatchAllInvocationEvents();
    }
    Collection<HighlightInfo> infos = DaemonCodeAnalyzerImpl.getHighlights(getDocument(psiFile), null, getProject());
    assertEmpty(infos);
    UIUtil.dispatchAllInvocationEvents();
  }
}
