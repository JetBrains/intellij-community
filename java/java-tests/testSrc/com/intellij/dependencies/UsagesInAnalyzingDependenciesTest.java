package com.intellij.dependencies;

import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.JavaAnalysisScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.packageDependencies.BackwardDependenciesBuilder;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.FindDependencyUtil;
import com.intellij.packageDependencies.ForwardDependenciesBuilder;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.TextChunk;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;

import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: Jan 18, 2005
 */
public class UsagesInAnalyzingDependenciesTest extends PsiTestCase{
  protected void setUp() throws Exception {
    super.setUp();

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          try{
            String root = JavaTestUtil.getJavaTestDataPath() + "/dependencies/search/" + getTestName(true);
            PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk17());
            PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
          }
          catch(Exception e){
            LOG.error(e);
          }
        }
      }
    );
  }

  public void testForwardPackageScope(){
    final PsiPackage bPackage = JavaPsiFacade.getInstance(myPsiManager.getProject()).findPackage("com.b");
    final DependenciesBuilder builder = new ForwardDependenciesBuilder(myProject, new JavaAnalysisScope(bPackage, null));
    builder.analyze();
    final Set<PsiFile> searchFor = new HashSet<PsiFile>();
    searchFor.add(myJavaFacade.findClass("com.a.A", GlobalSearchScope.allScope(myProject)).getContainingFile());
    final Set<PsiFile> searchIn = new HashSet<PsiFile>();
    final PsiClass bClass = myJavaFacade.findClass("com.b.B", GlobalSearchScope.allScope(myProject));
    searchIn.add(bClass.getContainingFile());
    final PsiClass cClass = myJavaFacade.findClass("com.b.C", GlobalSearchScope.allScope(myProject));
    searchIn.add(cClass.getContainingFile());
    final UsageInfo[] usagesInfos = FindDependencyUtil.findDependencies(builder, searchIn, searchFor);
    final UsageInfo2UsageAdapter[] usages = UsageInfo2UsageAdapter.convert(usagesInfos);
    final String [] psiUsages = new String [usagesInfos.length];
    for (int i = 0; i < usagesInfos.length; i++) {
      psiUsages[i] = toString(usages[i]);
    }
    checkResult(new String []{"(2, 14) import com.a.A;","(4, 3) A myA = new A();", "(4, 15) A myA = new A();", "(6, 9) myA.aa();",
                              "(2, 14) import com.a.A;","(4, 3) A myA = new A();", "(4, 15) A myA = new A();", "(6, 9) myA.aa();"}, psiUsages);
  }

  private static String toString(Usage usage) {
    TextChunk[] textChunks = usage.getPresentation().getText();
    StringBuffer result = new StringBuffer();
    for (TextChunk textChunk : textChunks) {
      result.append(textChunk);
    }

    return result.toString();
  }

   public void testBackwardPackageScope(){
     final PsiPackage bPackage = JavaPsiFacade.getInstance(myPsiManager.getProject()).findPackage("com.a");
    final DependenciesBuilder builder = new BackwardDependenciesBuilder(myProject, new JavaAnalysisScope(bPackage, null));
    builder.analyze();
    final Set<PsiFile> searchFor = new HashSet<PsiFile>();
    searchFor.add(myJavaFacade.findClass("com.a.A", GlobalSearchScope.allScope(myProject)).getContainingFile());
    final Set<PsiFile> searchIn = new HashSet<PsiFile>();
    final PsiClass bClass = myJavaFacade.findClass("com.b.B", GlobalSearchScope.allScope(myProject));
    searchIn.add(bClass.getContainingFile());
    final PsiClass cClass = myJavaFacade.findClass("com.a.C", GlobalSearchScope.allScope(myProject));
    searchFor.add(cClass.getContainingFile());
    final UsageInfo[] usagesInfos = FindDependencyUtil.findBackwardDependencies(builder, searchIn, searchFor);
    final UsageInfo2UsageAdapter[] usages = UsageInfo2UsageAdapter.convert(usagesInfos);
    final String [] psiUsages = new String [usagesInfos.length];
    for (int i = 0; i < usagesInfos.length; i++) {
      psiUsages[i] = toString(usages[i]);
    }
    checkResult(new String []{"(4, 3) A myA = new A();", "(4, 15) A myA = new A();", "(5, 3) C myC = new C();", "(5, 15) C myC = new C();", "(7, 9) myA.aa();", "(8, 9) myC.cc();"}, psiUsages);
  }

  public void testForwardSimple(){
    final DependenciesBuilder builder = new ForwardDependenciesBuilder(myProject, new AnalysisScope(myProject));
    builder.analyze();

    final Set<PsiFile> searchIn = new HashSet<PsiFile>();
    final PsiClass aClass = myJavaFacade.findClass("A", GlobalSearchScope.allScope(myProject));
    searchIn.add(aClass.getContainingFile());
    final Set<PsiFile> searchFor = new HashSet<PsiFile>();
    final PsiClass bClass = myJavaFacade.findClass("B", GlobalSearchScope.allScope(myProject));
    searchFor.add(bClass.getContainingFile());

    final UsageInfo[] usagesInfos = FindDependencyUtil.findDependencies(builder, searchIn, searchFor);
    final UsageInfo2UsageAdapter[] usages = UsageInfo2UsageAdapter.convert(usagesInfos);
    final String [] psiUsages = new String [usagesInfos.length];
    for (int i = 0; i < usagesInfos.length; i++) {
      psiUsages[i] = toString(usages[i]);
    }
    checkResult(new String []{"(2, 3) B myB = new B();", "(2, 15) B myB = new B();", "(4, 9) myB.bb();"}, psiUsages);
  }

  private static void checkResult(final String[] usages, final String [] psiUsages) {
    assertEquals(usages.length , psiUsages.length);
    for (int i = 0; i < psiUsages.length; i++) {
      assertEquals(usages[i], psiUsages[i]);
    }
  }
}
