// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.hierarchy;

import com.intellij.JavaTestUtil;
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.ide.hierarchy.actions.BrowseTypeHierarchyAction;
import com.intellij.ide.hierarchy.call.CalleeMethodsTreeStructure;
import com.intellij.ide.hierarchy.call.CallerMethodsTreeStructure;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightDefaultConstructor;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.codeInsight.hierarchy.HierarchyViewTestBase;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class JavaCallHierarchyTest extends HierarchyViewTestBase {
  @Override
  protected @NotNull LanguageLevel getProjectLanguageLevel() {
    return LanguageLevel.JDK_16; // records are needed
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected String getBasePath() {
    return "ide/hierarchy/call/" + getTestName(false);
  }
  
  @Override
  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk18();
  }

  private void doCallerHierarchyTest(@NotNull String classFqn, @NotNull String methodName, String @NotNull ... fileNames) {
    doHierarchyTest(() -> {
      PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(classFqn, ProjectScope.getProjectScope(getProject()));
      assertNotNull("Class '" + classFqn + "' not found", psiClass);
      PsiMember method = psiClass.findMethodsByName(methodName, false) [0];
      assertNotNull("Method '" + methodName + "' not found in " + classFqn + ". Available methods are " +
                    Arrays.toString(psiClass.getMethods()), method);
      return new CallerMethodsTreeStructure(getProject(), method, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(myProject), fileNames);
  }

  private void doCalleeHierarchyTest(@NotNull String classFqn, @NotNull String methodName, String @NotNull ... fileNames) {
    doHierarchyTest(() -> {
      PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(classFqn, ProjectScope.getProjectScope(getProject()));
      PsiMember method = psiClass.findMethodsByName(methodName, false) [0];
      return new CalleeMethodsTreeStructure(getProject(), method, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(myProject),fileNames);
  }

  public void testDirectRecursion() {
    doCallerHierarchyTest("A", "recursive", "A.java");
  }

  public void testCalleeDirectRecursion() {
    doCalleeHierarchyTest("A", "recursive", "A.java");
  }

  public void testIndirectRecursion() {
    doCallerHierarchyTest("A", "recursive2", "A.java");
  }

  public void testIdeaDev41005() {
    doCallerHierarchyTest("B", "xyzzy", "A.java");
  }

  public void testIdeaDev41005_Inheritance() {
    doCallerHierarchyTest("D", "xyzzy", "A.java");
  }

  public void testIdeaDev41005_Sibling() {
    doCallerHierarchyTest("D", "xyzzy", "A.java");
  }

  public void testIdeaDev41005_SiblingUnderInheritance() {
    doCallerHierarchyTest("D", "xyzzy", "A.java");
  }

  public void testIdeaDev41232() {
    doCallerHierarchyTest("A", "main", "A.java");
  }

  public void testDefaultConstructor() {
    doCallerHierarchyTest("A", "A", "A.java");
  }
  
  public void testDefaultConstructors() {
    doHierarchyTest(() -> {
      PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass("Sweet", ProjectScope.getProjectScope(getProject()));
      return new CallerMethodsTreeStructure(getProject(), LightDefaultConstructor.create(aClass), HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(myProject), "Tastes.java");
  }
  
  public void testDefaultConstructorsReverse() {
    doCalleeHierarchyTest("Bitter", "main", "Tastes.java");
  }

  public void testRecordCanonicalConstructor() {
    doHierarchyTest(() -> {
      PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass("Person", ProjectScope.getProjectScope(getProject()));
      return new CallerMethodsTreeStructure(getProject(), aClass, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(myProject), "Action.java");
  }

  public void testRecordCanonicalConstructorReverse() {
    doHierarchyTest(() -> {
      PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass("Person", ProjectScope.getProjectScope(getProject()));
      return new CalleeMethodsTreeStructure(getProject(), aClass, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(myProject), "Action.java");
  }

  public void testRecordCanonicalConstructorReverse2() {
    doHierarchyTest(() -> {
      PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass("Value", ProjectScope.getProjectScope(getProject()));
      return new CalleeMethodsTreeStructure(getProject(), aClass, HierarchyBrowserBaseEx.SCOPE_ALL);
    }, JavaHierarchyUtil.getComparator(myProject), "Value.java");
  }

  public void testRecordComponent() {
    doHierarchyTest(() -> {
      PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass("Value", ProjectScope.getProjectScope(getProject()));
      PsiRecordComponent component = aClass.getRecordComponents()[0];
      return new CallerMethodsTreeStructure(getProject(), component, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(myProject), "Value.java");
  }

  public void testMethodRef() {
    doCalleeHierarchyTest("A", "testMethod", "A.java");
  }

  public void testField() {
    doHierarchyTest(() -> {
      PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass("A", ProjectScope.getProjectScope(getProject()));
      PsiField field = psiClass.findFieldByName("testField", false);
      return new CallerMethodsTreeStructure(getProject(), field, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(myProject),"A.java");
  }

  public void testStaticallyImportedField() {
    doHierarchyTest(() -> {
      PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass("java.util.Collections", ProjectScope.getAllScope(getProject()));
      PsiMember field = psiClass.findFieldByName("EMPTY_LIST", false);
      return new CallerMethodsTreeStructure(getProject(), field, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(myProject), "A.java");
  }

  public void testAnonymous() {
    doCallerHierarchyTest("A", "A", "A.java"); // IDEA-56615
  }

  public void testAnonymous2() {
    doCallerHierarchyTest("A", "doIt", "A.java");
  }

  public void testAnonymous3() {
    doCallerHierarchyTest("B", "foo", "A.java"); // IDEA-140031
  }

  public void testActionAvailableInXml() {
    configureByText(XmlFileType.INSTANCE, "<foo>java.lang.Str<caret>ing</foo>");
    BrowseTypeHierarchyAction action = new BrowseTypeHierarchyAction();
    AnActionEvent e = TestActionEvent.createTestEvent(action);
    ActionUtil.updateAction(action, e);
    assertTrue(e.getPresentation().isEnabledAndVisible());
  }

  public void testMustIgnoreJavadocReferences() {
    doCallerHierarchyTest("p.X", "persist", "X.java");
  }
  public void testCallersOfBaseMethod() {
    doCallerHierarchyTest("p.BaseClass", "method", "X.java");
  }
  public void testCallersOfSubMethod() {
    doCallerHierarchyTest("p.BaseClass", "method", "X.java");
  }
  public void testEnclosingDeps() {
    doCallerHierarchyTest("DummyImpl", "doSth", "A.java");
  }
  public void testThroughAnonymous() {
    doCallerHierarchyTest("com.hierarchytest.AcmClientImpl", "getUser", "X.java");
  }
  public void testThroughAnonymousCalledByOther() {
    doCallerHierarchyTest("x.AcmClientImpl", "returnSomething", "X.java");
  }
  public void testWildcards() {
    doCallerHierarchyTest("p.BoardImpl", "getCount", "A.java");
  }
}
