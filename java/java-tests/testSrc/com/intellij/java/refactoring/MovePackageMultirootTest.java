/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.MultipleRootsMoveDestination;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class MovePackageMultirootTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @Test
  public void testMovePackage() {
    doTest((rootDir, rootAfter) -> {
      JavaPsiFacade facade = JavaPsiFacade.getInstance(heavyFixture.get().getProject());

      PsiPackage pack1 = facade.findPackage("pack1");
      assertEquals(2, pack1.getDirectories().length);
      assertEquals(1, facade.findPackage("pack1.inside").getDirectories().length);
      assertEquals(1, facade.findPackage("pack1Unrelated").getDirectories().length);

      PsiPackage[] sourcePackages = new PsiPackage[]{pack1};
      PsiPackage targetPackage = facade.findPackage("target");
      move(sourcePackages, targetPackage, true, true);
      FileDocumentManager.getInstance().saveAllDocuments();

      assertEquals(2, facade.findPackage("target.pack1").getDirectories().length);
      assertEquals(1, facade.findPackage("target.pack1.inside").getDirectories().length);
      assertEquals(1, facade.findPackage("pack1Unrelated").getDirectories().length);
    });
  }

  @Test
  public void testMoveFilesToEmptyPkg1() {
    doMovementTest(Set.of("pack1.S1"), "pack1.pack2");
  }

  @Test
  public void testMoveFilesToEmptyPkg2() {
    doMovementTest(Set.of("pack1.S1", "pack1.S2"), "pack1.pack2");
  }

  @Test
  public void testMoveFilesToNonEmptyPkg1() {
    doMovementTestWithConflicts(Set.of("pack1.S1"), "pack1.pack2",
                                "Changing exports/opens statements of package <b><code>pack1.pack2</code></b> grants access to other types and its members in the same package");
  }

  @Test
  public void testMoveFilesToNonEmptyPkg2() {
    doMovementTest(Set.of("pack1.S1"), "pack1.pack2");
  }

  @Test
  public void testMoveFilesToNonEmptyPkg3() {
    doMovementTest(Set.of("pack1.S1", "pack3.S4", "pack4.S6", "pack5.S7"), "pack1.pack2");
  }

  private void prepareProject(VirtualFile rootDir) {
    PsiTestUtil.addContentRoot(heavyFixture.get().getModule(), rootDir);
    final VirtualFile[] children = rootDir.getChildren();
    for (VirtualFile child : children) {
      String name = child.getName();
      if (name.startsWith("src")) {
        String prefix = name.equals("srcPrefix1") ? "pack1.inside" :
                        name.equals("srcPrefix2") ? "pack1Unrelated" :
                        "";
        PsiTestUtil.addSourceRoot(heavyFixture.get().getModule(), child, JavaSourceRootType.SOURCE,
                                  JpsJavaExtensionService.getInstance().createSourceRootProperties(prefix, false));
      }
    }
  }

  private void doMovementTest(@NotNull Set<String> sourceClassNames, @NotNull String targetPackageName) {
    IdeaTestUtil.setProjectLanguageLevel(heavyFixture.get().getProject(), LanguageLevel.JDK_1_9);
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> {
      doTest((rootDir, rootAfter) -> {
        JavaPsiFacadeEx facade = JavaPsiFacadeEx.getInstanceEx(heavyFixture.get().getProject());
        PsiClass[] sourceClasses = sourceClassNames.stream().map(name -> facade.findClass(name, GlobalSearchScope.allScope(heavyFixture.get().getProject())))
          .toArray(PsiClass[]::new);
        PsiPackage targetPackage = facade.findPackage(targetPackageName);
        move(sourceClasses, targetPackage, true, false);
        FileDocumentManager.getInstance().saveAllDocuments();
      });
    });
  }

  private void doMovementTestWithConflicts(@NotNull Set<String> sourceClassNames,
                                           @NotNull String targetPackageName,
                                           @NotNull String message) {
    IdeaTestUtil.setProjectLanguageLevel(heavyFixture.get().getProject(), LanguageLevel.JDK_1_9);
    doTest((rootDir, rootAfter) -> {
      JavaPsiFacadeEx facade = JavaPsiFacadeEx.getInstanceEx(heavyFixture.get().getProject());
      PsiClass[] sourceClasses = sourceClassNames.stream().map(name -> facade.findClass(name)).toArray(PsiClass[]::new);
      PsiPackage targetPackage = facade.findPackage(targetPackageName);
      try {
        move(sourceClasses, targetPackage, true, false);
      }
      catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
        assertEquals(message, e.getMessage());
        return;
      }
      fail("Unreachable statement");
    });
  }

  /**
   * Runs the test of {@code MultiFileTestCase.doTest} with the test data directory of the test name.
   */
  private void doTest(@NotNull HeavyJavaCodeInsightFixture.MultiFileAction action) {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    fixture.doMultiFileTest("/refactoring/movePackageMultiroot/" + fixture.getTestName(true), this::prepareProject, true, action);
  }

  protected void move(PsiElement[] elements, PsiPackage targetPackage, boolean searchInComments, boolean searchInNonJavaFiles) {
    new MoveClassesOrPackagesProcessor(heavyFixture.get().getProject(), elements,
                                       new MultipleRootsMoveDestination(new PackageWrapper(targetPackage)), searchInComments,
                                       searchInNonJavaFiles, null).run();
  }
}