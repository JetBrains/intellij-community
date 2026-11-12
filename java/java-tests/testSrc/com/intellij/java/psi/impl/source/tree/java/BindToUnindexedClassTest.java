// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.impl.source.tree.java;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
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
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Binds a reference to a class which a {@link ModCommand} creates in a copy of a directory. The index does not know such a class.
 */
public class BindToUnindexedClassTest extends LightJavaCodeInsightFixtureTestCase {
  public void testClassInAnotherPackage() {
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
    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("b.Foo", GlobalSearchScope.projectScope(getProject())));
  }

  public void testClassInSamePackage() {
    String text = """
      package a;

      class Test {
        Foo foo;
      }
      """;
    configure(text);
    bindToNewClass("a");
    myFixture.checkResult(text);
    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("a.Foo", GlobalSearchScope.projectScope(getProject())));
  }

  private void configure(@NotNull String text) {
    PsiFile file = myFixture.addFileToProject("a/Test.java", text);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
  }

  /**
   * Creates the class {@code Foo} in the package, and binds the type of the field to it in the same command.
   */
  private void bindToNewClass(@NotNull String packageName) {
    PsiJavaFile file = (PsiJavaFile)myFixture.getFile();
    PsiTypeElement typeElement = Objects.requireNonNull(file.getClasses()[0].getFields()[0].getTypeElement());
    PsiJavaCodeReferenceElement reference = Objects.requireNonNull(typeElement.getInnermostComponentReferenceElement());
    PsiPackage aPackage = Objects.requireNonNull(JavaPsiFacade.getInstance(getProject()).findPackage(packageName));
    PsiDirectory directory = aPackage.getDirectories()[0];
    ActionContext context = ActionContext.from(myFixture.getEditor(), file);
    ModCommand command = ModCommand.psiUpdate(context, updater -> {
      PsiJavaCodeReferenceElement writableReference = updater.getWritable(reference);
      PsiClass aClass = JavaDirectoryService.getInstance().createClass(updater.getWritable(directory), "Foo");
      writableReference.bindToElement(aClass);
    });
    ModCommandExecutor.executeInteractively(context, "", myFixture.getEditor(), () -> command);
  }
}
