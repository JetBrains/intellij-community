package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ExcludeFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.Nullable;

public class JavaContentEntryEditor extends ContentEntryEditor {
  private final CompilerModuleExtension myCompilerExtension;

  public JavaContentEntryEditor(ContentEntry contentEntry, ModifiableRootModel rootModel) {
    super(contentEntry, rootModel);
    myCompilerExtension = rootModel.getModuleExtension(CompilerModuleExtension.class);
  }

  protected ContentRootPanel createContentRootPane() {
    return new JavaContentRootPanel(myContentEntry, this);
  }

  @Nullable
  @Override
  protected ExcludeFolder doAddExcludeFolder(VirtualFile file) {
    final boolean isCompilerOutput = isCompilerOutput(file);
    boolean isExplodedDirectory = isExplodedDirectory(file);
    if (isCompilerOutput || isExplodedDirectory) {
      if (isCompilerOutput) {
        myCompilerExtension.setExcludeOutput(true);
      }
      if (isExplodedDirectory) {
        myRootModel.setExcludeExplodedDirectory(true);
      }
      return null;
    }
    return super.doAddExcludeFolder(file);
  }

  @Override
  protected void doRemoveExcludeFolder(ExcludeFolder excludeFolder, VirtualFile file) {
    if (isCompilerOutput(file)) {
      myCompilerExtension.setExcludeOutput(false);
    }
    if (isExplodedDirectory(file)) {
      myRootModel.setExcludeExplodedDirectory(false);
    }
    super.doRemoveExcludeFolder(excludeFolder, file);
  }

  private boolean isCompilerOutput(@Nullable VirtualFile file) {
    final VirtualFile compilerOutputPath = myCompilerExtension.getCompilerOutputPath();
    if (compilerOutputPath != null) {
      if (compilerOutputPath.equals(file)) {
        return true;
      }
    }

    final VirtualFile compilerOutputPathForTests = myCompilerExtension.getCompilerOutputPathForTests();
    if (compilerOutputPathForTests != null) {
      if (compilerOutputPathForTests.equals(file)) {
        return true;
      }
    }

    if (myCompilerExtension.isCompilerOutputPathInherited()) {
      final String compilerOutput = ProjectStructureConfigurable.getInstance(myRootModel.getModule().getProject()).getProjectConfig().getCompilerOutputUrl();
      if (file != null && Comparing.equal(compilerOutput, file.getUrl())) {
        return true;
      }
    }

    return false;
  }

  private boolean isExplodedDirectory(VirtualFile file) {
    final VirtualFile explodedDir = myRootModel.getExplodedDirectory();
    if (explodedDir != null) {
      if (explodedDir.equals(file)) {
        return true;
      }
    }
    return false;
  }

}
