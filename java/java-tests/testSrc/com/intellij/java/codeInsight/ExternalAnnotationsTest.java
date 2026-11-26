// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.AddAnnotationModCommandAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.i18n.I18nInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaModuleExternalPaths;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaProjectBuilderCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ExternalAnnotationsTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture =
    javaProjectBuilderCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/externalAnnotations", (projectBuilder, ignored) -> {
      JavaModuleFixtureBuilder<?> builder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
      builder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);
    });

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Module myModule = myFixture.getModule();
    ModuleRootModificationUtil.updateModel(myModule, model -> {
      DefaultLightProjectDescriptor.addJetBrainsAnnotationsWithTypeUse(model);
      String contentUrl = VfsUtilCore.pathToUrl(myFixture.getTempDirPath());
      model.addContentEntry(contentUrl).addSourceFolder(contentUrl + "/src", false);
      final JavaModuleExternalPaths extension = model.getModuleExtension(JavaModuleExternalPaths.class);
      extension.setExternalAnnotationUrls(new String[]{VfsUtilCore.pathToUrl(myFixture.getTempDirPath() + "/content/anno")});
    });

    Project myProject = myFixture.getProject();

    JavaCodeStyleSettings.getInstance(myProject).USE_EXTERNAL_ANNOTATIONS = true;
  }

  @Test
  public void testSafeDelete() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new UnusedSymbolLocalInspection());
    myFixture.configureByFiles("src/safeDelete/Hello.java", "content/anno/safeDelete/annotations.xml");
    EdtTestUtil.runInEdtAndWait(() -> {
      IntentionAction intention = myFixture.getAvailableIntention("Safe delete 'test(List<String>)'");
      myFixture.launchAction(intention);
      myFixture.checkResultByFile("content/anno/safeDelete/annotations.xml",
                                  "content/anno/safeDelete/annotations_after.xml",
                                  true);
    });
  }

  @Test
  public void testMakeExplicit() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("src/makeExplicit/Hello.java", "content/anno/makeExplicit/annotations.xml");
    EdtTestUtil.runInEdtAndWait(() -> {
      IntentionAction intention = myFixture.getAvailableIntention("Insert '@Contract(pure=true) @NotNull'");
      myFixture.launchAction(intention);
      myFixture.checkResultByFile("content/anno/makeExplicit/annotations.xml",
                                  "content/anno/makeExplicit/annotations_after.xml",
                                  true);
      myFixture.checkResultByFile("src/makeExplicit/Hello.java",
                                  "src/makeExplicit/Hello_after.java",
                                  true);
    });
  }

  @Test
  public void testMakeExplicitParam() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("src/makeExplicitParam/Hello.java", "content/anno/makeExplicitParam/annotations.xml");
    EdtTestUtil.runInEdtAndWait(() -> {
      IntentionAction intention = myFixture.getAvailableIntention("Insert '@NotNull @Unmodifiable'");
      myFixture.launchAction(intention);
      myFixture.checkResultByFile("content/anno/makeExplicitParam/annotations.xml",
                                  "content/anno/makeExplicitParam/annotations_after.xml",
                                  true);
      myFixture.checkResultByFile("src/makeExplicitParam/Hello.java",
                                  "src/makeExplicitParam/Hello_after.java",
                                  true);
    });
  }

  @Test
  public void testDeannotate() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("src/deannotate/Hello.java", "content/anno/deannotate/annotations.xml");
    EdtTestUtil.runInEdtAndWait(() -> {
      IntentionAction intention = myFixture.getAvailableIntention(JavaBundle.message("deannotate.intention.action.several.text"));
      myFixture.launchAction(intention);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResultByFile("content/anno/deannotate/annotations.xml",
                                  "content/anno/deannotate/annotations_after.xml",
                                  true);
    });
  }

  @Test
  public void testAddedAnnotationInCodeWhenAlreadyPresent() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("src/withAnnotation/Foo.java");
    EdtTestUtil.runInEdtAndWait(() -> {
      IdeaTestUtil.setModuleLanguageLevel(myFixture.getModule(), LanguageLevel.JDK_1_8);
      PsiMethod method = PsiTreeUtil.getParentOfType(myFixture.getElementAtCaret(), PsiMethod.class, false);
      assertNotNull(method);
      ActionContext context = myFixture.getActionContext();
      ModCommandExecutor.executeInteractively(
        context, "", myFixture.getEditor(),
        () -> AddAnnotationModCommandAction.createAddNullableFix(method).perform(context));
      myFixture.checkResultByFile("src/withAnnotation/Foo_after.java");
    });
  }

  @Test
  public void testRenameClassWithExternalAnnotations() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("src/rename/Foo.java", "content/anno/rename/annotations.xml");
    EdtTestUtil.runInEdtAndWait(() -> {

      myFixture.renameElementAtCaret("Bar");

      myFixture.checkResultByFile("content/anno/rename/annotations.xml",
                                  "content/anno/rename/annotations_after.xml",
                                  true);
    });
  }

  @Test
  public void testHardcodedStringLiteralWithExternalPackageAnnotation() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("src/i18n/Foo.java", "content/anno/i18n/annotations.xml");
    I18nInspection inspection = new I18nInspection();
    inspection.setIgnoreForAllButNls(true);
    myFixture.enableInspections(inspection);

    EdtTestUtil.runInEdtAndWait(() -> myFixture.testHighlighting(true, false, false, "src/i18n/Foo.java"));
  }

  @Test
  public void testBringToSrc() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("src/toSrc/Foo.java", "content/anno/toSrc/annotations.xml");
    EdtTestUtil.runInEdtAndWait(() -> {

      IntentionAction action = myFixture.findSingleIntention("Insert '@Deprecated'");
      assertNotNull(action);

      myFixture.launchAction(action);

      myFixture.checkResultByFile("src/toSrc/Foo_after.java");
      myFixture.checkResultByFile("content/anno/toSrc/annotations.xml",
                                  "content/anno/toSrc/annotations_after.xml",
                                  true);
    });
  }

  @Test
  public void testFromSrcToExternal() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("src/fromSrc/Foo.java", "content/anno/fromSrc/annotations.xml");
    EdtTestUtil.runInEdtAndWait(() -> {

      IntentionAction action = myFixture.findSingleIntention(JavaBundle.message("intention.text.annotate.externally"));
      assertNotNull(action);

      myFixture.launchAction(action);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();

      myFixture.checkResultByFile("src/fromSrc/Foo_after.java");
      myFixture.checkResultByFile("content/anno/fromSrc/annotations.xml",
                                  "content/anno/fromSrc/annotations_after.xml",
                                  true);
    });
  }

  @Test
  public void testExternalAnnotationsRootRemoved() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("src/rootRemoved/Foo.java", "content/anno/rootRemoved/annotations.xml");
    Project project = myFixture.getProject();
    PsiClass aClass = ReadAction.computeBlocking(() -> JavaPsiFacade.getInstance(project)
      .findClass("rootRemoved.Foo", GlobalSearchScope.projectScope(project)));
    assertNotNull(aClass);
    assertTrue(ReadAction.computeBlocking(() -> aClass.isDeprecated()));
    ModuleRootModificationUtil.updateModel(myFixture.getModule(), model -> {
      final JavaModuleExternalPaths extension = model.getModuleExtension(JavaModuleExternalPaths.class);
      extension.setExternalAnnotationUrls(ArrayUtil.EMPTY_STRING_ARRAY);
    });
    assertFalse(ReadAction.computeBlocking(() -> aClass.isDeprecated()));
  }
}
