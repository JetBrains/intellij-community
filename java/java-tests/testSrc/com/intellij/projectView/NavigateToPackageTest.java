// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

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
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public final class NavigateToPackageTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return new LightProjectDescriptor() {
      @Override
      protected void configureModule(@NotNull Module module,
                                     @NotNull ModifiableRootModel model,
                                     @NotNull ContentEntry contentEntry) {
        super.configureModule(module, model, contentEntry);
        IntelliJProjectConfiguration.LibraryRoots junit4Library = IntelliJProjectConfiguration.getProjectLibrary("JUnit4");
        ModuleRootModificationUtil.addModuleLibrary(module, "JUnit4", 
                                                    junit4Library.getClassesUrls(), 
                                                    junit4Library.getSourcesUrls(), 
                                                    Collections.emptyList(), 
                                                    DependencyScope.COMPILE, false);
      }
    };
  }

  public void testNavigateToLibraryRoot() {
    myFixture.configureByText("FooTest.java", "import org.jun<caret>it.*; class FooTest {}");
    PsiElement elementAtCaret = myFixture.getElementAtCaret();
    assertInstanceOf(elementAtCaret, PsiPackage.class);
    PsiDirectory[] directories = PsiPackageImplementationHelperImpl.suggestMostAppropriateDirectories(((PsiPackage)elementAtCaret));
    assertSize(1, directories);
  }
}
