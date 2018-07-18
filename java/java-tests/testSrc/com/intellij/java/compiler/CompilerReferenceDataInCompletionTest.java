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
package com.intellij.java.compiler;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.function.Predicate;

@SkipSlowTestLocally
public class CompilerReferenceDataInCompletionTest extends CompilerReferencesTestBase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.setTestDataPath(getTestDataPath() + getName());
    installCompiler();
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/compiler/completionOrdering/";
  }

  public void testSimpleMethods() {
    doTestMemberCompletionOrdering(new String[] {"Bar.java", "Foo.java"}, "qwe(0)", "asd(0)", "zxc(0)");
  }

  public void testSimpleMembers() {
    doTestMemberCompletionOrdering(new String[] {"Bar.java", "Foo.java"}, "qwe", "zxc(0)", "asd(0)");
  }

  //public void testOverloads() {
  //  doTestMemberCompletionOrdering(new String[] {"Bar.java", "Foo.java"}, "asd(3)", "asd(0)");
  //}

  public void _testConstructor() {
    doTestConstructorCompletionOrdering(new String[] {"Foo.java"}, "List l = new ", "LinkedList", "ArrayList");
  }

  public void testConstructorSumOccurrences() {
    doTestConstructorCompletionOrdering(new String[] {"Foo.java"}, "A a = new ", "B", "C");
  }

  public void testConstructorSumOccurrences2() {
    doTestConstructorCompletionOrdering(new String[] {"Foo.java"}, "A a = new ", "B", "C");
  }

  public void _testAnonymous() {
    doTestConstructorCompletionOrdering(new String[] {"Foo.java"}, "List l = new ", "AbstractList", "ArrayList");
  }

  public void testHelperMethodIsNotAffected() {
    doTestStaticMemberCompletionOrdering(new String[] {"Foo.java"}, "someMethod2(1)", "someMethod1(0)", "nonNull(1)", "m(0)");
  }

  public void testExpectedByTypeAreFirst() {
    doTestCompletion(new String[] {"Foo.java"}, "String s = ", new String[] {"someMethod2(1)", "someMethod1(0)", "someMethod3(0)", "m(0)", "mm(1)"}, m -> {
      PsiClass aClass = m.getContainingClass();
      return aClass != null && "Foo".equals(aClass.getName());
    });
  }

  private void doTestConstructorCompletionOrdering(@NotNull String[] files,
                                                   @NotNull String phraseToComplete,
                                                   String... expectedOrder) {
    doTestCompletion(files, phraseToComplete, expectedOrder, m -> m instanceof PsiClass && ArrayUtil.contains(m.getName(), expectedOrder));
  }

  private void doTestMemberCompletionOrdering(@NotNull String[] files, String... expectedOrder) {
    doTestCompletion(files, "foo.", expectedOrder, m -> "Foo".equals(m.getContainingClass().getName()));
  }

  private void doTestStaticMemberCompletionOrdering(@NotNull String[] files, String... expectedOrder) {
    doTestCompletion(files, "", expectedOrder, (PsiMember m) -> {
      PsiClass aClass = m.getContainingClass();
      return aClass != null && "Foo".equals(aClass.getName());
    });
  }

  private void doTestCompletion(@NotNull String[] files,
                                @NotNull String phraseToComplete,
                                @NotNull String[] expectedOrder,
                                @NotNull Predicate<PsiMember> resultFilter) {
    myFixture.configureByFiles(files);
    myFixture.type(phraseToComplete);
    final int offset = myFixture.getCaretOffset();
    final LookupElement[] completionVariantsBeforeCompilation = myFixture.complete(CompletionType.BASIC);
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> myFixture.getDocument(myFixture.getFile()).deleteString(offset - phraseToComplete.length(), offset));
    rebuildProject();
    myFixture.type(phraseToComplete);
    final LookupElement[] completionVariantsAfterCompilation = myFixture.complete(CompletionType.BASIC);

    assertFalse("Seems the test doesn't test anything: compiler indices doesn't affect on sorting",
                Arrays.toString(completionVariantsBeforeCompilation).equals(Arrays.toString(completionVariantsAfterCompilation)));

    final String[] orderedMethods = Arrays.stream(completionVariantsAfterCompilation)
      .map(l -> l.getObject())
      .filter(o -> o instanceof PsiMember)
      .map(o -> ((PsiMember)o))
      .filter(resultFilter)
      .map(CompilerReferenceDataInCompletionTest::getPresentation)
      .toArray(String[]::new);

    assertOrderedEquals(orderedMethods, expectedOrder);
  }

  private static String getPresentation(PsiMember member) {
    if (member instanceof PsiMethod) {
      return member.getName() + "(" + ((PsiMethod)member).getParameterList().getParametersCount() + ")";
    }
    else if (member instanceof PsiField || member instanceof PsiClass) {
      return member.getName();
    }
    fail("Unexpected member = " + member + " type = " + member.getClass());
    return null;
  }
}
