package com.intellij.psi.search;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class InheritorsTest extends PsiTestCase{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.search.InheritorsTest");

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    String root = JavaTestUtil.getJavaTestDataPath() + "/psi/search/inheritors/" + getTestName(true);
    PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk17());
    PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
  }

  public void testScope() throws Exception {
    doTest("pack1.Base", "pack1", true, "pack1.Derived1", "pack1.Derived3");
  }

  public void testNoScanJdk() throws Exception {
    doTest("javax.swing.JPanel", "", false);
  }

  public void testSameNamedClasses() throws Exception {
    doTest("x.Test", "", true, "x.Goo", "x.Zoo");
  }

  private void doTest(String className, String packageScopeName, final boolean deep, String... inheritorNames) throws Exception {
    final PsiClass aClass = myJavaFacade.findClass(className);
    assertNotNull(aClass);

    final SearchScope scope;
    if (packageScopeName != null){
      PsiPackage aPackage = JavaPsiFacade.getInstance(myPsiManager.getProject()).findPackage(packageScopeName);
      scope = PackageScope.packageScope(aPackage, true).intersectWith(GlobalSearchScope.projectScope(myProject));
    }
    else{
      scope = GlobalSearchScope.projectScope(myProject);
    }

    final ArrayList<String> inheritorsList = new ArrayList<String>();
    ProgressManager.getInstance().runProcess(
      new Runnable() {
        @Override
        public void run() {
          ClassInheritorsSearch.search(aClass, scope, deep).forEach(new PsiElementProcessorAdapter<PsiClass>(new PsiElementProcessor<PsiClass>() {
            @Override
            public boolean execute(@NotNull PsiClass element) {
              inheritorsList.add(element.getQualifiedName());
              return true;
            }
          }));
        }
      },
      null
    );

    assertSameElements(inheritorsList, inheritorNames);
  }
}
