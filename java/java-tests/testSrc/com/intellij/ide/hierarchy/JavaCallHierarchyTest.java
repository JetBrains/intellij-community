package com.intellij.ide.hierarchy;

import com.intellij.JavaTestUtil;
import com.intellij.ide.hierarchy.actions.BrowseTypeHierarchyAction;
import com.intellij.ide.hierarchy.call.CallerMethodsTreeStructure;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.codeInsight.hierarchy.HierarchyViewTestBase;

/**
 * @author yole
 */
public class JavaCallHierarchyTest extends HierarchyViewTestBase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected String getBasePath() {
    return "ide/hierarchy/call/" + getTestName(false);
  }

  private void doJavaCallTypeHierarchyTest(final String classFqn, final String methodName, final String... fileNames) throws Exception {
    doHierarchyTest(new Computable<HierarchyTreeStructure>() {
      @Override
      public HierarchyTreeStructure compute() {
        final PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(classFqn, ProjectScope.getProjectScope(getProject()));
        final PsiMethod method = psiClass.findMethodsByName(methodName, false) [0];
        return new CallerMethodsTreeStructure(getProject(), method, HierarchyBrowserBaseEx.SCOPE_PROJECT);
      }
    }, fileNames);
  }

  public void testIdeaDev41005() throws Exception {
    doJavaCallTypeHierarchyTest("B", "xyzzy", "B.java", "D.java", "A.java");
  }

  public void testIdeaDev41005_Inheritance() throws Exception {
    doJavaCallTypeHierarchyTest("D", "xyzzy", "B.java", "D.java", "A.java", "C.java");
  }

  public void testIdeaDev41005_Sibling() throws Exception {
    doJavaCallTypeHierarchyTest("D", "xyzzy", "B.java", "D.java", "A.java", "C.java");
  }

  public void testIdeaDev41005_SiblingUnderInheritance() throws Exception {
    doJavaCallTypeHierarchyTest("D", "xyzzy", "B.java", "D.java", "A.java", "C.java", "CChild.java");
  }

  public void testIdeaDev41232() throws Exception {
    doJavaCallTypeHierarchyTest("A", "main", "B.java", "A.java");
  }

  public void testAnonymous2() throws Exception {
    doJavaCallTypeHierarchyTest("A", "doIt", "A.java");
  }

  public void testActionAvailableInXml() throws Exception {
    configureByText(XmlFileType.INSTANCE, "<foo>java.lang.Str<caret>ing</foo>");
    BrowseTypeHierarchyAction action = new BrowseTypeHierarchyAction();
    TestActionEvent e = new TestActionEvent(action);
    action.beforeActionPerformedUpdate(e);
    assertTrue(e.getPresentation().isEnabled() && e.getPresentation().isVisible());
  }
}
