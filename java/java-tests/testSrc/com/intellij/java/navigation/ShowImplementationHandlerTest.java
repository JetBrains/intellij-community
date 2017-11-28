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

package com.intellij.java.navigation;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.hint.actions.ShowImplementationsAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.Navigatable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class ShowImplementationHandlerTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    super.tuneFixture(moduleBuilder);
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8);
  }

  public void testMultipleImplsFromAbstractCall() {
    PsiFile file = myFixture.addFileToProject("Foo.java", "public abstract class Hello {" +
                                                          "    {" +
                                                          "        Runnable r = () <caret>-> {};\n" +
                                                          "    }\n" +
                                                          "}\n" +
                                                          "\n");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement element =
      TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());
    assertTrue(element instanceof PsiClass);
    final String qualifiedName = ((PsiClass)element).getQualifiedName();
    assertEquals(CommonClassNames.JAVA_LANG_RUNNABLE, qualifiedName);
  }

  public void testFunctionExpressionsOnReference() {
    myFixture.addClass("public interface I {void m();}");
    myFixture.addClass("public class Usage {{I i = () -> {};}}");
    PsiFile file = myFixture.addFileToProject("Foo.java", "public abstract class Hello {" +
                                                          "    void foo(I i) {" +
                                                          "        i.<caret>m();\n" +
                                                          "    }\n" +
                                                          "}\n" +
                                                          "\n");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement[] implementations = getImplementations();
    assertEquals(2, implementations.length);
    assertInstanceOf(implementations[1], PsiLambdaExpression.class);
  }

  private static PsiElement[] getImplementations() {
    final Ref<PsiElement[]> ref = new Ref<>();
    new ShowImplementationsAction() {
      @Override
      protected void showImplementations(@NotNull PsiElement[] impls, @NotNull Project project, String text, Editor editor, PsiFile file,
                                         PsiElement element,
                                         boolean invokedFromEditor,
                                         boolean invokedByShortcut) {
        ref.set(impls);
      }
    }.performForContext(DataManager.getInstance().getDataContext());
    return ref.get();
  }

  public void testEnumValuesNavigation() {
    final PsiFile file = myFixture.addFileToProject("Foo.java", "public class Foo {" +
                                                                "  public enum E {;}" +
                                                                "  void foo() {" +
                                                                "    for (E e : E.va<caret>lues()){}" +
                                                                "  }" +
                                                                "}");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    final PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    assertInstanceOf(element, PsiMethod.class);
    assertTrue(((Navigatable)element).canNavigate());
    ((Navigatable)element).navigate(true);
    assertEquals(32, myFixture.getCaretOffset());
  }
}