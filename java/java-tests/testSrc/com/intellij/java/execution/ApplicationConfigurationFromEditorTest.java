// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution;

import com.intellij.JavaTestUtil;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.application.AbstractApplicationConfigurationProducer;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCompiledFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class ApplicationConfigurationFromEditorTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testApplicationConfigurationForUnknownMethod() {
    assertNull(setupConfigurationContext("""
                                           public class Foo {
                                             public static void x<caret>xx(String[] args) {}
                                           }"""));
    assertNotNull(setupConfigurationContext("""
                                              public class Foo {
                                                public static void m<caret>ain(String[] args) {}
                                              }"""));
  }

  @Test
  public void testNoDecompilation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String path = JavaTestUtil.getJavaTestDataPath() + "/psi/cls/mirror/pkg/SimpleEnum.class";
    VirtualFile
      file = (path.contains("!/") ? StandardFileSystems.jar() : StandardFileSystems.local()).refreshAndFindFileByPath(path);
    PsiFile psiFile = ReadAction.computeBlocking(() -> PsiManager.getInstance(myFixture.getProject()).findFile(file));
    ConfigurationContext context = ReadAction.computeBlocking(() -> ConfigurationContext.createEmptyContextForLocation(new PsiLocation<>(psiFile)));
    TestProducer producer = new TestProducer();
    producer.setupConfigurationFromContextForTest(new ApplicationConfiguration("some", myFixture.getProject()), context, new Ref<>(psiFile));
    PsiCompiledFile binaryFile = (PsiCompiledFile)psiFile;
    assertNull(binaryFile.getCachedMirror());
  }

  private <T> T setupConfigurationContext(final String fileText) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("MyTest.java", fileText);

    return ReadAction.computeBlocking(() -> {
      MapDataContext dataContext = new MapDataContext();
      dataContext.put(CommonDataKeys.PROJECT, myFixture.getProject());
      dataContext.put(CommonDataKeys.EDITOR, myFixture.getEditor());
      dataContext.put(CommonDataKeys.PSI_FILE, myFixture.getFile());

      ConfigurationContext context = ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN);
      RunnerAndConfigurationSettings settings = context.getConfiguration();
      //noinspection unchecked
      return settings != null ? (T)settings.getConfiguration() : null;
    });
  }

  private static class TestProducer extends AbstractApplicationConfigurationProducer<ApplicationConfiguration> {
    public void setupConfigurationFromContextForTest(@NotNull ApplicationConfiguration configuration,
                                                     @NotNull ConfigurationContext context,
                                                     @NotNull Ref<PsiElement> sourceElement) {
      super.setupConfigurationFromContext(configuration, context, sourceElement);
    }
  }
}
