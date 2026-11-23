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
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.impl.light.LightDefaultConstructor;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.codeInsight.hierarchy.HierarchyViewFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.intellij.testFramework.codeInsight.hierarchy.HierarchyViewFixture.hierarchyViewFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaCallHierarchyTest {
  private final TestFixture<HierarchyViewFixture> hierarchyFixture =
    hierarchyViewFixture(JavaTestUtil.getJavaTestDataPath(), "ide/hierarchy/call", () -> IdeaTestUtil.getMockJdk18(),
                         LanguageLevel.JDK_16); // records are needed

  private void doCallerHierarchyTest(@NotNull String classFqn, @NotNull String methodName, String @NotNull ... fileNames) {
    HierarchyViewFixture fixture = hierarchyFixture.get();
    Project project = fixture.getProject();
    fixture.doHierarchyTest(() -> {
      PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(classFqn, ProjectScope.getProjectScope(project));
      assertNotNull(psiClass, "Class '" + classFqn + "' not found");
      PsiMember method = psiClass.findMethodsByName(methodName, false) [0];
      assertNotNull(method, "Method '" + methodName + "' not found in " + classFqn + ". Available methods are " +
                            Arrays.toString(psiClass.getMethods()));
      return new CallerMethodsTreeStructure(project, method, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(project), fileNames);
  }

  private void doCalleeHierarchyTest(@NotNull String classFqn, @NotNull String methodName, String @NotNull ... fileNames) {
    HierarchyViewFixture fixture = hierarchyFixture.get();
    Project project = fixture.getProject();
    fixture.doHierarchyTest(() -> {
      PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(classFqn, ProjectScope.getProjectScope(project));
      PsiMember method = psiClass.findMethodsByName(methodName, false) [0];
      return new CalleeMethodsTreeStructure(project, method, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(project),fileNames);
  }

  @Test
  public void testDirectRecursion() {
    doCallerHierarchyTest("A", "recursive", "A.java");
  }

  @Test
  public void testCalleeDirectRecursion() {
    doCalleeHierarchyTest("A", "recursive", "A.java");
  }

  @Test
  public void testIndirectRecursion() {
    doCallerHierarchyTest("A", "recursive2", "A.java");
  }

  @Test
  public void testIdeaDev41005() {
    doCallerHierarchyTest("B", "xyzzy", "A.java");
  }

  @Test
  public void testIdeaDev41005_Inheritance() {
    doCallerHierarchyTest("D", "xyzzy", "A.java");
  }

  @Test
  public void testIdeaDev41005_Sibling() {
    doCallerHierarchyTest("D", "xyzzy", "A.java");
  }

  @Test
  public void testIdeaDev41005_SiblingUnderInheritance() {
    doCallerHierarchyTest("D", "xyzzy", "A.java");
  }

  @Test
  public void testIdeaDev41232() {
    doCallerHierarchyTest("A", "main", "A.java");
  }

  @Test
  public void testDefaultConstructor() {
    doCallerHierarchyTest("A", "A", "A.java");
  }
  
  @Test
  public void testDefaultConstructors() {
    HierarchyViewFixture fixture = hierarchyFixture.get();
    Project project = fixture.getProject();
    fixture.doHierarchyTest(() -> {
      PsiClass aClass = JavaPsiFacade.getInstance(project).findClass("Sweet", ProjectScope.getProjectScope(project));
      return new CallerMethodsTreeStructure(project, LightDefaultConstructor.create(aClass), HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(project), "Tastes.java");
  }
  
  @Test
  public void testDefaultConstructorsReverse() {
    doCalleeHierarchyTest("Bitter", "main", "Tastes.java");
  }

  @Test
  public void testRecordCanonicalConstructor() {
    HierarchyViewFixture fixture = hierarchyFixture.get();
    Project project = fixture.getProject();
    fixture.doHierarchyTest(() -> {
      PsiClass aClass = JavaPsiFacade.getInstance(project).findClass("Person", ProjectScope.getProjectScope(project));
      return new CallerMethodsTreeStructure(project, aClass, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(project), "Action.java");
  }

  @Test
  public void testRecordCanonicalConstructorReverse() {
    HierarchyViewFixture fixture = hierarchyFixture.get();
    Project project = fixture.getProject();
    fixture.doHierarchyTest(() -> {
      PsiClass aClass = JavaPsiFacade.getInstance(project).findClass("Person", ProjectScope.getProjectScope(project));
      return new CalleeMethodsTreeStructure(project, aClass, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(project), "Action.java");
  }

  @Test
  public void testRecordCanonicalConstructorReverse2() {
    HierarchyViewFixture fixture = hierarchyFixture.get();
    Project project = fixture.getProject();
    fixture.doHierarchyTest(() -> {
      PsiClass aClass = JavaPsiFacade.getInstance(project).findClass("Value", ProjectScope.getProjectScope(project));
      return new CalleeMethodsTreeStructure(project, aClass, HierarchyBrowserBaseEx.SCOPE_ALL);
    }, JavaHierarchyUtil.getComparator(project), "Value.java");
  }

  @Test
  public void testRecordComponent() {
    HierarchyViewFixture fixture = hierarchyFixture.get();
    Project project = fixture.getProject();
    fixture.doHierarchyTest(() -> {
      PsiClass aClass = JavaPsiFacade.getInstance(project).findClass("Value", ProjectScope.getProjectScope(project));
      PsiRecordComponent component = aClass.getRecordComponents()[0];
      return new CallerMethodsTreeStructure(project, component, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(project), "Value.java");
  }

  @Test
  public void testMethodRef() {
    doCalleeHierarchyTest("A", "testMethod", "A.java");
  }

  @Test
  public void testField() {
    HierarchyViewFixture fixture = hierarchyFixture.get();
    Project project = fixture.getProject();
    fixture.doHierarchyTest(() -> {
      PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass("A", ProjectScope.getProjectScope(project));
      PsiField field = psiClass.findFieldByName("testField", false);
      return new CallerMethodsTreeStructure(project, field, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(project),"A.java");
  }

  @Test
  public void testStaticallyImportedField() {
    HierarchyViewFixture fixture = hierarchyFixture.get();
    Project project = fixture.getProject();
    fixture.doHierarchyTest(() -> {
      PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass("java.util.Collections", ProjectScope.getAllScope(project));
      PsiMember field = psiClass.findFieldByName("EMPTY_LIST", false);
      return new CallerMethodsTreeStructure(project, field, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(project), "A.java");
  }

  @Test
  public void testAnonymous() {
    doCallerHierarchyTest("A", "A", "A.java"); // IDEA-56615
  }

  @Test
  public void testAnonymous2() {
    doCallerHierarchyTest("A", "doIt", "A.java");
  }

  @Test
  public void testAnonymous3() {
    doCallerHierarchyTest("B", "foo", "A.java"); // IDEA-140031
  }

  @Test
  public void testActionAvailableInXml() {
    hierarchyFixture.get().configureByText(XmlFileType.INSTANCE, "<foo>java.lang.Str<caret>ing</foo>");
    BrowseTypeHierarchyAction action = new BrowseTypeHierarchyAction();
    AnActionEvent e = TestActionEvent.createTestEvent(action);
    EdtTestUtil.runInEdtAndWait(() -> ActionUtil.updateAction(action, e));
    assertTrue(e.getPresentation().isEnabledAndVisible());
  }

  @Test
  public void testMustIgnoreJavadocReferences() {
    doCallerHierarchyTest("p.X", "persist", "X.java");
  }
  @Test
  public void testCallersOfBaseMethod() {
    doCallerHierarchyTest("p.BaseClass", "method", "X.java");
  }
  @Test
  public void testCallersOfSubMethod() {
    doCallerHierarchyTest("p.BaseClass", "method", "X.java");
  }
  @Test
  public void testEnclosingDeps() {
    doCallerHierarchyTest("DummyImpl", "doSth", "A.java");
  }
  @Test
  public void testThroughAnonymous() {
    doCallerHierarchyTest("com.hierarchytest.AcmClientImpl", "getUser", "X.java");
  }
  @Test
  public void testThroughAnonymousCalledByOther() {
    doCallerHierarchyTest("x.AcmClientImpl", "returnSomething", "X.java");
  }
  @Test
  public void testWildcards() {
    doCallerHierarchyTest("p.BoardImpl", "getCount", "A.java");
  }
}
