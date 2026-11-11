// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.projectView;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.file.PsiPackageImplementationHelperImpl;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@TestApplication
public final class NavigateToPackageTest {
  private static final LightProjectDescriptor DESCRIPTOR = new LightProjectDescriptor() {
    @Override
    protected void configureModule(@NotNull Module module,
                                   @NotNull ModifiableRootModel model,
                                   @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      IntelliJProjectConfiguration.LibraryRoots junit4Library = IntelliJProjectConfiguration.getModuleLibrary("intellij.libraries.junit4", "JUnit4");
      ModuleRootModificationUtil.addModuleLibrary(module, "JUnit4", 
                                                  junit4Library.getClassesUrls(), 
                                                  junit4Library.getSourcesUrls(), 
                                                  Collections.emptyList(), 
                                                  DependencyScope.COMPILE, false);
    }
  };
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testNavigateToLibraryRoot() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("FooTest.java", "import org.jun<caret>it.*; class FooTest {}");
    PsiElement elementAtCaret = ReadAction.computeBlocking(() -> myFixture.getElementAtCaret());
    assertInstanceOf(PsiPackage.class, elementAtCaret);
    PsiDirectory[] directories = ReadAction.computeBlocking(
      () -> PsiPackageImplementationHelperImpl.suggestMostAppropriateDirectories(((PsiPackage)elementAtCaret)));
    assertThat(directories).hasSize(1);
  }
}
