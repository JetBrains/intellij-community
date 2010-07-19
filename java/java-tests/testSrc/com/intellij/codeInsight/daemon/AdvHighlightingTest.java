package com.intellij.codeInsight.daemon;

import com.intellij.analysis.PackagesScopesProvider;
import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
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
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.io.File;
import java.util.Collection;

/**
 * This class intended for "heavily-loaded" tests only, e.g. those need to setup separate project directory structure to run
 * For "lightweight" tests use LightAdvHighlightingTest
 */
public class AdvHighlightingTest extends DaemonAnalyzerTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting";

  public void testPackageLocals() throws Exception { doTest(BASE_PATH+"/packageLocals/x/sub/UsingMain.java", BASE_PATH+"/packageLocals", false, false); }
  public void testPackageLocalClassInTheMiddle() throws Exception { doTest(BASE_PATH+"/packageLocals/x/A.java", BASE_PATH+"/packageLocals", false, false); }

  public void testEffectiveAccessLevel() throws Exception { doTest(BASE_PATH+"/accessLevel/effectiveAccess/p2/p3.java", BASE_PATH+"/accessLevel", false, false); }
  public void testSingleImportConflict() throws Exception { doTest(BASE_PATH+"/singleImport/d.java", BASE_PATH+"/singleImport", false, false); }

  public void testDuplicateTopLevelClass() throws Exception { doTest(BASE_PATH+"/duplicateClass/A.java", BASE_PATH+"/duplicateClass", false, false); }
  public void testDuplicateTopLevelClass2() throws Exception { doTest(BASE_PATH+"/duplicateClass/java/lang/Runnable.java", BASE_PATH+"/duplicateClass", false, false); }

  public void testProtectedConstructorCall() throws Exception { doTest(BASE_PATH+"/protectedConstructor/p2/C2.java", BASE_PATH+"/protectedConstructor", false, false); }
  public void testProtectedConstructorCallInSamePackage() throws Exception { doTest(BASE_PATH+"/protectedConstructor/samePackage/C2.java", BASE_PATH+"/protectedConstructor", false, false); }
  public void testProtectedConstructorCallInInner() throws Exception { doTest(BASE_PATH+"/protectedConstructorInInner/p2/C2.java", BASE_PATH+"/protectedConstructorInInner", false, false); }
  public void testArrayLengthAccessFromSubClass() throws Exception { doTest(BASE_PATH+"/arrayLength/p2/SubTest.java", BASE_PATH+"/arrayLength", false, false); }
  public void testAccessibleMember() throws Exception { doTest(BASE_PATH+"/accessibleMember/com/red/C.java", BASE_PATH+"/accessibleMember", false, false); }
  public void testOnDemandImportConflict() throws Exception { doTest(BASE_PATH+"/onDemandImportConflict/Outer.java", BASE_PATH+"/onDemandImportConflict", false, false); }
  public void testPackageLocalOverride() throws Exception { doTest(BASE_PATH+"/packageLocalOverride/y/C.java", BASE_PATH+"/packageLocalOverride", false, false); }
  public void testPackageLocalOverrideJustCheckThatPackageLocalMethodDoesNotGetOverridden() throws Exception { doTest(BASE_PATH+"/packageLocalOverride/y/B.java", BASE_PATH+"/packageLocalOverride", false, false); }
  public void testProtectedAccessFromOtherPackage() throws Exception { doTest(BASE_PATH+"/protectedAccessFromOtherPackage/a/Main.java", BASE_PATH+"/protectedAccessFromOtherPackage", false, false); }
  public void testProtectedFieldAccessFromOtherPackage() throws Exception { doTest(BASE_PATH+"/protectedAccessFromOtherPackage/a/A.java", BASE_PATH+"/protectedAccessFromOtherPackage", false, false); }
  public void testPackageLocalClassInTheMiddle1() throws Exception { doTest(BASE_PATH+"/foreignPackageInBetween/a/A1.java", BASE_PATH+"/foreignPackageInBetween", false, false); }

  protected Sdk getTestProjectJdk() {
    @NonNls boolean is50 = "StaticImportConflict".equals(getTestName(false));
    LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(is50 ? LanguageLevel.JDK_1_5 : LanguageLevel.JDK_1_4);
    return is50 ? JavaSdkImpl.getMockJdk17("java 1.5") : JavaSdkImpl.getMockJdk14();
  }

  public void testStaticImportConflict() throws Exception { doTest(BASE_PATH+"/staticImportConflict/Usage.java", BASE_PATH+"/staticImportConflict", false, false); }
  public void testImportOnDemand() throws Exception { doTest(BASE_PATH+"/importOnDemand/y/Y.java", BASE_PATH+"/importOnDemand", false, false); }
  public void testImportOnDemandVsSingle() throws Exception { doTest(BASE_PATH+"/importOnDemandVsSingle/y/Y.java", BASE_PATH+"/importOnDemandVsSingle", false, false); }
  public void testImportSingleVsSamePackage() throws Exception { doTest(BASE_PATH+"/importSingleVsSamePackage/y/Y.java", BASE_PATH+"/importSingleVsSamePackage", false, false); }

  public void testOverridePackageLocal() throws Exception { doTest(BASE_PATH+"/overridePackageLocal/x/y/Derived.java", BASE_PATH+"/overridePackageLocal", false, false); }
  public void testAlreadyImportedClass() throws Exception { doTest(BASE_PATH+"/alreadyImportedClass/pack/AlreadyImportedClass.java", BASE_PATH+"/alreadyImportedClass", false, false); }
  public void testImportDefaultPackage() throws Exception { doTest(BASE_PATH+"/importDefaultPackage/x/Usage.java", BASE_PATH+"/importDefaultPackage", false, false); }
  public void testImportDefaultPackage2() throws Exception { doTest(BASE_PATH+"/importDefaultPackage/x/ImportOnDemandUsage.java", BASE_PATH+"/importDefaultPackage", false, false); }

  public void testScopeBased() throws Exception {
    NamedScope xscope = new NamedScope("xxx", new PatternPackageSet("x..*", PatternPackageSet.SCOPE_SOURCE, null));
    NamedScope utilscope = new NamedScope("util", new PatternPackageSet("java.util.*", PatternPackageSet.SCOPE_LIBRARY, null));
    NamedScopeManager scopeManager = NamedScopeManager.getInstance(getProject());
    scopeManager.addScope(xscope);
    scopeManager.addScope(utilscope);

    EditorColorsManager manager = EditorColorsManager.getInstance();
    EditorColorsScheme scheme = (EditorColorsScheme)manager.getGlobalScheme().clone();
    manager.addColorsScheme(scheme);
    EditorColorsManager.getInstance().setGlobalScheme(scheme);
    TextAttributesKey xKey = ColorAndFontOptions.getScopeTextAttributeKey(xscope.getName());
    TextAttributes xAttributes = new TextAttributes(Color.cyan, Color.darkGray, Color.blue, EffectType.BOXED, Font.ITALIC);
    scheme.setAttributes(xKey, xAttributes);

    TextAttributesKey utilKey = ColorAndFontOptions.getScopeTextAttributeKey(utilscope.getName());
    TextAttributes utilAttributes = new TextAttributes(Color.gray, Color.magenta, Color.orange, EffectType.STRIKEOUT, Font.BOLD);
    scheme.setAttributes(utilKey, utilAttributes);

    try {
      doTest(BASE_PATH+"/scopeBased/x/X.java", BASE_PATH+"/scopeBased", false, true);
    }
    finally {
      scopeManager.removeAllSets();
    }
  }
  public void testSharedScopeBased() throws Exception {
    NamedScope xscope = new NamedScope("xxx", new PatternPackageSet("x..*", PatternPackageSet.SCOPE_ANY, null));
    NamedScope utilscope = new NamedScope("util", new PatternPackageSet("java.util.*", PatternPackageSet.SCOPE_LIBRARY, null));
    NamedScopesHolder scopeManager = DependencyValidationManager.getInstance(getProject());
    scopeManager.addScope(xscope);
    scopeManager.addScope(utilscope);

    EditorColorsManager manager = EditorColorsManager.getInstance();
    EditorColorsScheme scheme = (EditorColorsScheme)manager.getGlobalScheme().clone();
    manager.addColorsScheme(scheme);
    EditorColorsManager.getInstance().setGlobalScheme(scheme);
    TextAttributesKey xKey = ColorAndFontOptions.getScopeTextAttributeKey(xscope.getName());
    TextAttributes xAttributes = new TextAttributes(Color.cyan, Color.darkGray, Color.blue, null, Font.ITALIC);
    scheme.setAttributes(xKey, xAttributes);

    TextAttributesKey utilKey = ColorAndFontOptions.getScopeTextAttributeKey(utilscope.getName());
    TextAttributes utilAttributes = new TextAttributes(Color.gray, Color.magenta, Color.orange, EffectType.STRIKEOUT, Font.BOLD);
    scheme.setAttributes(utilKey, utilAttributes);

    NamedScope projectScope = PackagesScopesProvider.getInstance(myProject).getProjectProductionScope();
    TextAttributesKey projectKey = ColorAndFontOptions.getScopeTextAttributeKey(projectScope.getName());
    TextAttributes projectAttributes = new TextAttributes(null, null, Color.blue, EffectType.BOXED, Font.ITALIC);
    scheme.setAttributes(projectKey, projectAttributes);

    try {
      doTest(BASE_PATH+"/scopeBased/x/Shared.java", BASE_PATH+"/scopeBased", false, true);
    }
    finally {
      scopeManager.removeAllSets();
    }
  }

  public void testMultiJDKConflict() throws Exception {
    String path = PathManagerEx.getTestDataPath() + BASE_PATH + "/" + getTestName(true);
    VirtualFile root = LocalFileSystem.getInstance().findFileByIoFile(new File(path));
    loadAllModulesUnder(root);
    ModuleManager moduleManager = ModuleManager.getInstance(getProject());
    final Module java4 = moduleManager.findModuleByName("java4");
    Module java5 = moduleManager.findModuleByName("java5");
    final ModuleRootManager rootManager4 = ModuleRootManager.getInstance(java4);
    final ModuleRootManager rootManager5 = ModuleRootManager.getInstance(java5);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final ModifiableRootModel rootModel4 = rootManager4.getModifiableModel();
        rootModel4.setSdk(JavaSdkImpl.getMockJdk17("java 1.4"));
        rootModel4.commit();
        final ModifiableRootModel rootModel5 = rootManager5.getModifiableModel();
        rootModel5.setSdk(JavaSdkImpl.getMockJdk17("java 1.5"));
        rootModel5.addModuleOrderEntry(java4);
        rootModel5.commit();
      }
    });

    configureByExistingFile(root.findFileByRelativePath("moduleJava5/com/Java5.java"));
    Collection<HighlightInfo> infos = highlightErrors();
    assertEmpty(infos);
  }

  public void testSameFQNClasses() throws Exception {
    String path = PathManagerEx.getTestDataPath() + BASE_PATH + "/" + getTestName(true);
    VirtualFile root = LocalFileSystem.getInstance().findFileByIoFile(new File(path));
    loadAllModulesUnder(root);

    configureByExistingFile(root.findFileByRelativePath("client/src/BugTest.java"));
    Collection<HighlightInfo> infos = highlightErrors();
    assertEmpty(infos);
  }

  public void testSameClassesInSourceAndLib() throws Exception {
    String path = PathManagerEx.getTestDataPath() + BASE_PATH + "/" + getTestName(true);
    VirtualFile root = LocalFileSystem.getInstance().findFileByIoFile(new File(path));
    loadAllModulesUnder(root);

    configureByExistingFile(root.findFileByRelativePath("src/ppp/SomeClass.java"));
    PsiField field = ((PsiJavaFile)myFile).getClasses()[0].findFieldByName("f", false);
    PsiClass aClass = ((PsiClassType)field.getType()).resolve();
    assertEquals("ppp.BadClass", aClass.getQualifiedName());
    //lies in source
    assertEquals(myFile.getVirtualFile().getParent(), aClass.getContainingFile().getVirtualFile().getParent());
  }

}
