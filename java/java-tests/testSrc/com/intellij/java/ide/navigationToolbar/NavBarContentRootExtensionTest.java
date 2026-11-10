// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.navigationToolbar;

import com.intellij.ide.navigationToolbar.AbstractNavBarModelExtension;
import com.intellij.ide.navigationToolbar.NavBarModelExtension;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static com.intellij.platform.navbar.testFramework.TestFrameworkKt.compatibilityNavBarItemIsModuleContentRoot;
import static com.intellij.platform.navbar.testFramework.TestFrameworkKt.compatibilityNavBarItemTextAttributes;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Checks that {@link NavBarModelExtension#isModuleContentRoot} decides the module content root look in the nav bar.
 */
@TestApplication
public class NavBarContentRootExtensionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testExtensionMarksPlainDirectory() {
    PsiDirectory sub = subDirectory();
    useExtension(dir -> dir.equals(sub) ? Boolean.TRUE : null);
    assertContentRootLook(sub, true);
  }

  @Test
  public void testExtensionUnmarksContentRoot() {
    PsiDirectory root = contentRoot();
    useExtension(dir -> dir.equals(root) ? Boolean.FALSE : null);
    assertContentRootLook(root, false);
  }

  @Test
  public void testNullAsksProjectModel() {
    PsiDirectory root = contentRoot();
    PsiDirectory sub = subDirectory();
    useExtension(dir -> null);
    assertContentRootLook(root, true);
    assertContentRootLook(sub, false);
  }

  private PsiDirectory contentRoot() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiDirectory root = ReadAction.computeBlocking(() -> PsiManager.getInstance(myFixture.getProject()).findDirectory(ModuleRootManager.getInstance(myFixture.getModule()).getContentRoots()[0]));
    assertNotNull(root);
    return root;
  }

  private PsiDirectory subDirectory() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.addFileToProject("sub/a.txt", "");
    PsiDirectory sub = ReadAction.computeBlocking(() -> file.getContainingDirectory());
    assertNotNull(sub);
    return sub;
  }

  private void useExtension(@NotNull Function<PsiDirectory, Boolean> answer) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    NavBarModelExtension fake = new AbstractNavBarModelExtension() {
      @Override
      public @Nullable String getPresentableText(Object object) {
        return null;
      }

      @Override
      public @Nullable Boolean isModuleContentRoot(@NotNull PsiDirectory directory) {
        return answer.apply(directory);
      }
    };
    ExtensionTestUtil.maskExtensions(NavBarModelExtension.EP_NAME,
                                     ContainerUtil.prepend(NavBarModelExtension.EP_NAME.getExtensionList(), fake),
                                     myFixture.getTestRootDisposable());
  }

  private static void assertContentRootLook(@NotNull PsiDirectory dir, boolean expected) {
    ReadAction.runBlocking(() -> {
      assertEquals(Boolean.valueOf(expected), compatibilityNavBarItemIsModuleContentRoot(dir), dir.getName());
      assertEquals(expected, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES.equals(compatibilityNavBarItemTextAttributes(dir)), dir.getName());
    });
  }
}
