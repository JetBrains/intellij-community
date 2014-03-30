package com.intellij.navigation;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.navigation.ClassImplementationsSearch;
import com.intellij.codeInsight.navigation.MethodImplementationsSearch;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.CommonProcessors;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author cdr
 */
public class GotoImplementationTest extends CodeInsightTestCase {

  private static Collection<PsiClass> getClassImplementations(final PsiClass psiClass) {
    CommonProcessors.CollectProcessor<PsiClass> processor = new CommonProcessors.CollectProcessor<PsiClass>();
    ClassImplementationsSearch.processImplementations(psiClass, processor, psiClass.getUseScope());

    return processor.getResults();
  }

  @Override
  protected void setUpProject() throws Exception {
    final String root = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/navigation/alexProject";
    VirtualFile vfsRoot = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(root));

    VirtualFile projectFile = vfsRoot.findChild("test.ipr");
    myProject = ProjectManagerEx.getInstanceEx().loadProject(projectFile.getPath());

    simulateProjectOpen();
    ProjectManagerEx.getInstanceEx().openTestProject(myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    ((ModuleManagerImpl)ModuleManager.getInstance(myProject)).projectClosed();
    super.tearDown();
  }

  public void test() throws Exception {

    ModuleManager moduleManager = ModuleManager.getInstance(getProject());
    Module[] modules = moduleManager.getModules();
    assertEquals(3, modules.length);

    Module module1 = moduleManager.findModuleByName("test1");
    Module module2 = moduleManager.findModuleByName("test2");
    Module module3 = moduleManager.findModuleByName("test3");
    final GlobalSearchScope moduleScope = GlobalSearchScope.moduleScope(module1);
    PsiClass test1 = myJavaFacade.findClass("com.test.TestI", moduleScope);
    PsiClass test2 = myJavaFacade.findClass("com.test.TestI", GlobalSearchScope.moduleScope(module2));
    PsiClass test3 = myJavaFacade.findClass("com.test.TestI", GlobalSearchScope.moduleScope(module3));
    HashSet<PsiClass> expectedImpls1 = new HashSet<PsiClass>(Arrays.asList(
      myJavaFacade.findClass("com.test.TestIImpl1", moduleScope),
      myJavaFacade.findClass("com.test.TestIImpl2", moduleScope)
    ));
    assertEquals(expectedImpls1, new HashSet<PsiClass>(getClassImplementations(test1)));

    PsiMethod psiMethod = test1.findMethodsByName("test", false)[0];
    Set<PsiMethod> expectedMethodImpl1 = new HashSet<PsiMethod>(Arrays.asList(
      myJavaFacade.findClass("com.test.TestIImpl1", moduleScope).findMethodsByName("test",false)[0],
      myJavaFacade.findClass("com.test.TestIImpl2", moduleScope).findMethodsByName("test",false)[0]
    ));
    assertEquals(expectedMethodImpl1, new HashSet<PsiMethod>(Arrays.asList(MethodImplementationsSearch.getMethodImplementations(psiMethod, moduleScope))));

    HashSet<PsiClass> expectedImpls2 = new HashSet<PsiClass>(Arrays.asList(
      myJavaFacade.findClass("com.test.TestIImpl1", GlobalSearchScope.moduleScope(module2)),
      myJavaFacade.findClass("com.test.TestIImpl3", GlobalSearchScope.moduleScope(module2))
    ));
    assertEquals(expectedImpls2, new HashSet<PsiClass>(getClassImplementations(test2)));

    HashSet<PsiClass> expectedImpls3 = new HashSet<PsiClass>(Arrays.asList(
      myJavaFacade.findClass("com.test.TestIImpl1", GlobalSearchScope.moduleScope(module3))
    ));
    assertEquals(expectedImpls3, new HashSet<PsiClass>(getClassImplementations(test3)));

  }

}
