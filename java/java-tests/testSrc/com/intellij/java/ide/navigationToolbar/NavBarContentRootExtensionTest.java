// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.navigationToolbar;

import com.intellij.ide.navigationToolbar.AbstractNavBarModelExtension;
import com.intellij.ide.navigationToolbar.NavBarModelExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

import static com.intellij.platform.navbar.testFramework.TestFrameworkKt.compatibilityNavBarItemIsModuleContentRoot;
import static com.intellij.platform.navbar.testFramework.TestFrameworkKt.compatibilityNavBarItemTextAttributes;

/**
 * Checks that {@link NavBarModelExtension#isModuleContentRoot} decides the module content root look in the nav bar.
 */
public class NavBarContentRootExtensionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testExtensionMarksPlainDirectory() {
    PsiDirectory sub = subDirectory();
    useExtension(dir -> dir.equals(sub) ? Boolean.TRUE : null);
    assertContentRootLook(sub, true);
  }

  public void testExtensionUnmarksContentRoot() {
    PsiDirectory root = contentRoot();
    useExtension(dir -> dir.equals(root) ? Boolean.FALSE : null);
    assertContentRootLook(root, false);
  }

  public void testNullAsksProjectModel() {
    PsiDirectory root = contentRoot();
    PsiDirectory sub = subDirectory();
    useExtension(dir -> null);
    assertContentRootLook(root, true);
    assertContentRootLook(sub, false);
  }

  private PsiDirectory contentRoot() {
    PsiDirectory root = getPsiManager().findDirectory(ModuleRootManager.getInstance(getModule()).getContentRoots()[0]);
    assertNotNull(root);
    return root;
  }

  private PsiDirectory subDirectory() {
    PsiDirectory sub = myFixture.addFileToProject("sub/a.txt", "").getContainingDirectory();
    assertNotNull(sub);
    return sub;
  }

  private void useExtension(@NotNull Function<PsiDirectory, Boolean> answer) {
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
                                     getTestRootDisposable());
  }

  private static void assertContentRootLook(@NotNull PsiDirectory dir, boolean expected) {
    assertEquals(dir.getName(), Boolean.valueOf(expected), compatibilityNavBarItemIsModuleContentRoot(dir));
    assertEquals(dir.getName(), expected, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES.equals(compatibilityNavBarItemTextAttributes(dir)));
  }
}
