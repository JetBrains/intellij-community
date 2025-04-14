// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.intention.AddAnnotationModCommandAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.i18n.I18nInspection;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaModuleExternalPaths;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import com.intellij.util.ArrayUtil;

public class ExternalAnnotationsTest extends UsefulTestCase {
  private CodeInsightTestFixture myFixture;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());

    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    final String dataPath = PathManagerEx.getTestDataPath() + "/codeInsight/externalAnnotations";
    myFixture.setTestDataPath(dataPath);
    JavaModuleFixtureBuilder<?> builder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    builder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);

    myFixture.setUp();
    Module myModule = builder.getFixture().getModule();
    ModuleRootModificationUtil.updateModel(myModule, model -> {
      DefaultLightProjectDescriptor.addJetBrainsAnnotations(model);
      String contentUrl = VfsUtilCore.pathToUrl(myFixture.getTempDirPath());
      model.addContentEntry(contentUrl).addSourceFolder(contentUrl + "/src", false);
      final JavaModuleExternalPaths extension = model.getModuleExtension(JavaModuleExternalPaths.class);
      extension.setExternalAnnotationUrls(new String[]{VfsUtilCore.pathToUrl(myFixture.getTempDirPath() + "/content/anno")});
    });

    Project myProject = myFixture.getProject();

    JavaCodeStyleSettings.getInstance(myProject).USE_EXTERNAL_ANNOTATIONS = true;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myFixture = null;
      super.tearDown();
    }
  }

  public void testAddedAnnotationInCodeWhenAlreadyPresent() {
    myFixture.configureByFile("src/withAnnotation/Foo.java");
    PsiMethod method = PsiTreeUtil.getParentOfType(myFixture.getElementAtCaret(), PsiMethod.class, false);
    assertNotNull(method);
    ActionContext context = myFixture.getActionContext();
    ModCommandExecutor.executeInteractively(
      context, "", myFixture.getEditor(),
      () -> AddAnnotationModCommandAction.createAddNullableFix(method).perform(context));
    myFixture.checkResultByFile("src/withAnnotation/Foo_after.java");
  }

  public void testRenameClassWithExternalAnnotations() {
    myFixture.configureByFiles("src/rename/Foo.java", "content/anno/rename/annotations.xml");

    myFixture.renameElementAtCaret("Bar");

    myFixture.checkResultByFile("content/anno/rename/annotations.xml",
                                "content/anno/rename/annotations_after.xml",
                                true);
  }

  public void testHardcodedStringLiteralWithExternalPackageAnnotation() {
    myFixture.configureByFiles("src/i18n/Foo.java", "content/anno/i18n/annotations.xml");
    I18nInspection inspection = new I18nInspection();
    inspection.setIgnoreForAllButNls(true);
    myFixture.enableInspections(inspection);

    myFixture.testHighlighting(true, false, false, "src/i18n/Foo.java");
  }

  public void testBringToSrc() {
    myFixture.configureByFiles("src/toSrc/Foo.java", "content/anno/toSrc/annotations.xml");

    IntentionAction action = myFixture.findSingleIntention("Insert '@Deprecated'");
    assertNotNull(action);

    myFixture.launchAction(action);

    myFixture.checkResultByFile("src/toSrc/Foo_after.java");
    myFixture.checkResultByFile("content/anno/toSrc/annotations.xml",
                                "content/anno/toSrc/annotations_after.xml",
                                true);
  }

  public void testFromSrcToExternal() {
    myFixture.configureByFiles("src/fromSrc/Foo.java", "content/anno/fromSrc/annotations.xml");

    IntentionAction action = myFixture.findSingleIntention(JavaBundle.message("intention.text.annotate.externally"));
    assertNotNull(action);

    myFixture.launchAction(action);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();

    myFixture.checkResultByFile("src/fromSrc/Foo_after.java");
    myFixture.checkResultByFile("content/anno/fromSrc/annotations.xml",
                                "content/anno/fromSrc/annotations_after.xml",
                                true);

  }

  public void testExternalAnnotationsRootRemoved() {
    myFixture.configureByFiles("src/rootRemoved/Foo.java", "content/anno/rootRemoved/annotations.xml");
    Project project = myFixture.getProject();
    PsiClass aClass = JavaPsiFacade.getInstance(project)
      .findClass("rootRemoved.Foo", GlobalSearchScope.projectScope(project));
    assertNotNull(aClass);
    assertTrue(aClass.isDeprecated());
    ModuleRootModificationUtil.updateModel(myFixture.getModule(), model -> {
      final JavaModuleExternalPaths extension = model.getModuleExtension(JavaModuleExternalPaths.class);
      extension.setExternalAnnotationUrls(ArrayUtil.EMPTY_STRING_ARRAY);
    });
    assertFalse(aClass.isDeprecated());
  }
}
