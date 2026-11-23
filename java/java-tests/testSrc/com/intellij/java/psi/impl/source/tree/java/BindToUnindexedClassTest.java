// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.impl.source.tree.java;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Binds a reference to a class which a {@link ModCommand} creates in a copy of a directory. The index does not know such a class.
 */
@TestApplication
public class BindToUnindexedClassTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testClassInAnotherPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package b; public class Existing {}");
    configure("""
                package a;

                class Test {
                  Foo foo;
                }
                """);
    bindToNewClass("b");
    myFixture.checkResult("""
                            package a;

                            import b.Foo;

                            class Test {
                              Foo foo;
                            }
                            """);
    assertNotNull(ReadAction.computeBlocking(
      () -> JavaPsiFacade.getInstance(myFixture.getProject()).findClass("b.Foo", GlobalSearchScope.projectScope(myFixture.getProject()))));
  }

  @Test
  public void testClassInSamePackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String text = """
      package a;

      class Test {
        Foo foo;
      }
      """;
    configure(text);
    bindToNewClass("a");
    myFixture.checkResult(text);
    assertNotNull(ReadAction.computeBlocking(
      () -> JavaPsiFacade.getInstance(myFixture.getProject()).findClass("a.Foo", GlobalSearchScope.projectScope(myFixture.getProject()))));
  }

  private void configure(@NotNull String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.addFileToProject("a/Test.java", text);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
  }

  /**
   * Creates the class {@code Foo} in the package, and binds the type of the field to it in the same command.
   */
  private void bindToNewClass(@NotNull String packageName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiJavaFile file = (PsiJavaFile)myFixture.getFile();
      PsiTypeElement typeElement = Objects.requireNonNull(file.getClasses()[0].getFields()[0].getTypeElement());
      PsiJavaCodeReferenceElement reference = Objects.requireNonNull(typeElement.getInnermostComponentReferenceElement());
      PsiPackage aPackage = Objects.requireNonNull(JavaPsiFacade.getInstance(myFixture.getProject()).findPackage(packageName));
      PsiDirectory directory = aPackage.getDirectories()[0];
      ActionContext context = ActionContext.from(myFixture.getEditor(), file);
      ModCommand command = ModCommand.psiUpdate(context, updater -> {
        PsiJavaCodeReferenceElement writableReference = updater.getWritable(reference);
        PsiClass aClass = JavaDirectoryService.getInstance().createClass(updater.getWritable(directory), "Foo");
        writableReference.bindToElement(aClass);
      });
      ModCommandExecutor.executeInteractively(context, "", myFixture.getEditor(), () -> command);
    });
  }
}
