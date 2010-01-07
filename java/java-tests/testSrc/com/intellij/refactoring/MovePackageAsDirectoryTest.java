
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveDirectoryWithClassesProcessor;

import java.util.Arrays;
import java.util.Comparator;

public class MovePackageAsDirectoryTest extends MultiFileTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  protected String getTestRoot() {
    return "/refactoring/movePackageAsDir/";
  }

  public void testMovePackage() throws Exception {
    doTest(createAction("pack1", "target"));
  }

  public void testRemoveUnresolvedImports() throws Exception {
    doTest(createAction("pack1", "target"));
  }

  private PerformAction createAction(final String packageName, final String targetPackageName) {
    return new PerformAction() {
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
        final Comparator<PsiDirectory> directoryComparator = new Comparator<PsiDirectory>() {
          public int compare(PsiDirectory o1, PsiDirectory o2) {
            return o1.getVirtualFile().getPresentableUrl().compareTo(o2.getVirtualFile().getPresentableUrl());
          }
        };

        final PsiPackage sourcePackage = psiFacade.findPackage(packageName);
        assertNotNull(sourcePackage);
        final PsiDirectory[] srcDirectories = sourcePackage.getDirectories();
        assertEquals(srcDirectories.length, 2);
        Arrays.sort(srcDirectories, directoryComparator);

        final PsiPackage targetPackage = psiFacade.findPackage(targetPackageName);
        assertNotNull(targetPackage);
        final PsiDirectory[] targetDirectories = targetPackage.getDirectories();
        Arrays.sort(targetDirectories, directoryComparator);
        assertTrue(targetDirectories.length > 0);

        new MoveDirectoryWithClassesProcessor(getProject(), new PsiDirectory[]{srcDirectories[0]}, targetDirectories[0], false, false, true, null).run();
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    };
  }

  protected void setupProject(VirtualFile rootDir) {
    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
    final ContentEntry contentEntry = rootModel.addContentEntry(rootDir);
    final VirtualFile[] children = rootDir.getChildren();
    for (VirtualFile child : children) {
      if (child.getName().startsWith("src")) {
        contentEntry.addSourceFolder(child, false);
      }
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        rootModel.commit();
      }
    });
  }
}
