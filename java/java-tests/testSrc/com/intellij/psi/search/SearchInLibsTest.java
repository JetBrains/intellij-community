package com.intellij.psi.search;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public class SearchInLibsTest extends PsiTestCase {
  public void testSearchInProject() throws Exception {
    doTest("ProjectClass", new String[]{"ProjectClass.java"}, GlobalSearchScope.projectScope(myProject));
    doTest("LibraryClass1", new String[]{"ProjectClass.java"}, GlobalSearchScope.projectScope(myProject));
    doTest("LibraryClass2", new String[]{}, GlobalSearchScope.projectScope(myProject));
  }

  public void testSearchInLibs() throws Exception {
    doTest("ProjectClass", new String[]{"ProjectClass.java"}, GlobalSearchScope.allScope(myProject));
    doTest("LibraryClass1", new String[]{"LibraryClass2.java", "ProjectClass.java"}, GlobalSearchScope.allScope(myProject));
    doTest("LibraryClass2", new String[]{"LibraryClass1.java"}, GlobalSearchScope.allScope(myProject));
  }

  public void testInnerSourceRoot() throws Exception {
    doTest("ProjectClass2", new String[]{"ProjectClass2.java"}, GlobalSearchScope.projectScope(myProject));
  }

  private void doTest(String classNameToSearch, String[] expectedFileNames, SearchScope scope) throws Exception {

    String root = JavaTestUtil.getJavaTestDataPath() + "/psi/search/searchInLibs";
    VirtualFile rootFile = PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete, false);

    final VirtualFile projectRoot = rootFile.findChild("project");
    assertNotNull(projectRoot);

    final VirtualFile innerSourceRoot = projectRoot.findChild("src2");
    assertNotNull(innerSourceRoot);

    VirtualFile libRoot = rootFile.findChild("lib");
    final VirtualFile libClassesRoot = libRoot.findChild("classes");
    final VirtualFile libSrcRoot = libRoot.findChild("src");
    assertNotNull(libRoot);

    PsiTestUtil.removeAllRoots(myModule, null);
    PsiTestUtil.addSourceRoot(myModule, projectRoot);
    PsiTestUtil.addSourceRoot(myModule, innerSourceRoot);
    ModuleRootModificationUtil.addModuleLibrary(myModule, "lib", Collections.singletonList(libClassesRoot.getUrl()), Collections.singletonList(libSrcRoot.getUrl()));

    final PsiClass aClass = myJavaFacade.findClass(classNameToSearch);
    assertNotNull(aClass);

    PsiReference[] refs = ReferencesSearch.search(aClass, scope, false).toArray(new PsiReference[0]);

    ArrayList<PsiFile> files = new ArrayList<PsiFile>();
    for (int i = 0; i < refs.length; i++) {
      PsiReference ref = refs[i];
      PsiFile file = ref.getElement().getContainingFile();
      if (!files.contains(file)) {
        files.add(file);
      }
    }

    assertEquals("files count", expectedFileNames.length, files.size());

    Collections.sort(files, new Comparator() {
      @Override
      public int compare(Object o1, Object o2) {
        PsiFile file1 = (PsiFile) o1;
        PsiFile file2 = (PsiFile) o2;
        return file1.getName().compareTo(file2.getName());
      }
    });
    Arrays.sort(expectedFileNames);

    for (int i = 0; i < expectedFileNames.length; i++) {
      String name = expectedFileNames[i];
      PsiFile file = (PsiFile) files.get(i);
      assertEquals(name, file.getName());
    }
  }
}
