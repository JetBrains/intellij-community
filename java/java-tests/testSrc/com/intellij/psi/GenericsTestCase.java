package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.PsiTestCase;

import java.io.File;

/**
 * @author dsl
 */
public abstract class GenericsTestCase extends PsiTestCase {
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  protected ModifiableRootModel setupGenericSampleClasses() {
    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
    final String commonPath = PathManagerEx.getTestDataPath().replace(File.separatorChar, '/') + "/psi/types/src";
    final VirtualFile[] commonRoot = new VirtualFile[] { null };
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        commonRoot[0] = LocalFileSystem.getInstance().refreshAndFindFileByPath(commonPath);
      }
    });

    final ContentEntry commonContentEntry = rootModel.addContentEntry(commonRoot[0]);
    commonContentEntry.addSourceFolder(commonRoot[0], false);
    return rootModel;
  }
}
