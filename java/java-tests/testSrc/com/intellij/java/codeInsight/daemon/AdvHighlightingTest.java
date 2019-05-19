// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.analysis.PackagesScopesProvider;
import com.intellij.application.options.colors.ScopeAttributesUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PatternPackageSet;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.io.File;
import java.util.Collection;

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

  public void testPackageAndClassConflict12() throws Exception {
    doTest(BASE_PATH + "/packageClassClash1/pkg/sub.java", BASE_PATH + "/packageClassClash1", false, false);
  }

  public void testScopeBased() {
    NamedScope xScope = new NamedScope("xxx", new PatternPackageSet("x..*", PatternPackageSet.SCOPE_SOURCE, null));
    NamedScope utilScope = new NamedScope("util", new PatternPackageSet("java.util.*", PatternPackageSet.SCOPE_LIBRARY, null));
    NamedScopeManager scopeManager = NamedScopeManager.getInstance(getProject());
    scopeManager.addScope(xScope);
    scopeManager.addScope(utilScope);

    EditorColorsManager manager = EditorColorsManager.getInstance();
    EditorColorsScheme scheme = (EditorColorsScheme)manager.getGlobalScheme().clone();
    manager.addColorsScheme(scheme);
    EditorColorsManager.getInstance().setGlobalScheme(scheme);
    TextAttributesKey xKey = ScopeAttributesUtil.getScopeTextAttributeKey(xScope.getName());
    TextAttributes xAttributes = new TextAttributes(Color.cyan, Color.darkGray, Color.blue, EffectType.BOXED, Font.ITALIC);
    scheme.setAttributes(xKey, xAttributes);

    TextAttributesKey utilKey = ScopeAttributesUtil.getScopeTextAttributeKey(utilScope.getName());
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
    NamedScope xScope = new NamedScope("xxx", new PatternPackageSet("x..*", PatternPackageSet.SCOPE_ANY, null));
    NamedScope utilScope = new NamedScope("util", new PatternPackageSet("java.util.*", PatternPackageSet.SCOPE_LIBRARY, null));
    NamedScopesHolder scopeManager = DependencyValidationManager.getInstance(getProject());
    scopeManager.addScope(xScope);
    scopeManager.addScope(utilScope);

    EditorColorsManager manager = EditorColorsManager.getInstance();
    EditorColorsScheme scheme = (EditorColorsScheme)manager.getGlobalScheme().clone();
    manager.addColorsScheme(scheme);
    EditorColorsManager.getInstance().setGlobalScheme(scheme);
    TextAttributesKey xKey = ScopeAttributesUtil.getScopeTextAttributeKey(xScope.getName());
    TextAttributes xAttributes = new TextAttributes(Color.cyan, Color.darkGray, Color.blue, null, Font.ITALIC);
    scheme.setAttributes(xKey, xAttributes);

    TextAttributesKey utilKey = ScopeAttributesUtil.getScopeTextAttributeKey(utilScope.getName());
    TextAttributes utilAttributes = new TextAttributes(Color.gray, Color.magenta, Color.orange, EffectType.STRIKEOUT, Font.BOLD);
    scheme.setAttributes(utilKey, utilAttributes);

    NamedScope projectScope = PackagesScopesProvider.getInstance(myProject).getProjectProductionScope();
    TextAttributesKey projectKey = ScopeAttributesUtil.getScopeTextAttributeKey(projectScope.getName());
    TextAttributes projectAttributes = new TextAttributes(null, null, Color.blue, EffectType.BOXED, Font.ITALIC);
    scheme.setAttributes(projectKey, projectAttributes);

    try {
      testFile(BASE_PATH + "/scopeBased/x/Shared.java").projectRoot(BASE_PATH + "/scopeBased").checkSymbolNames().test();
    }
    finally {
      scopeManager.removeAllSets();
    }
  }

  public void testMultiJDKConflict() {
    String path = PathManagerEx.getTestDataPath() + BASE_PATH + "/" + getTestName(true);
    VirtualFile root = LocalFileSystem.getInstance().findFileByIoFile(new File(path));
    assert root != null : path;
    loadAllModulesUnder(root);

    ModuleManager moduleManager = ModuleManager.getInstance(getProject());
    Module java4 = moduleManager.findModuleByName("java4");
    Module java5 = moduleManager.findModuleByName("java5");
    ModuleRootModificationUtil.setModuleSdk(java4, IdeaTestUtil.getMockJdk17("java 1.4"));
    ModuleRootModificationUtil.setModuleSdk(java5, IdeaTestUtil.getMockJdk17("java 1.5"));
    ModuleRootModificationUtil.addDependency(java5, java4);

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
}
