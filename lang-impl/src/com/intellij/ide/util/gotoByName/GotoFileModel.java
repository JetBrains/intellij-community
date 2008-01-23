package com.intellij.ide.util.gotoByName;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GotoFileModel extends ContributorsBasedGotoByModel{
  private final int myMaxSize;

  public GotoFileModel(Project project) {
    super(project, Extensions.getExtensions(ChooseByNameContributor.FILE_EP_NAME));
    myMaxSize = WindowManagerEx.getInstanceEx().getFrame(project).getSize().width;
  }

  public String getPromptText() {
    return IdeBundle.message("prompt.gotofile.enter.file.name");
  }

  public String getCheckBoxName() {
    return IdeBundle.message("checkbox.include.java.files");
  }

  public char getCheckBoxMnemonic() {
    return 'j';
  }

  public String getNotInMessage() {
    return IdeBundle.message("label.no.non.java.files.found");
  }

  public String getNotFoundMessage() {
    return IdeBundle.message("label.no.files.found");
  }

  public boolean loadInitialCheckBoxState() {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    return propertiesComponent.isTrueValue("GoToClass.includeJavaFiles");
  }

  public void saveInitialCheckBoxState(boolean state) {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    propertiesComponent.setValue("GoToClass.includeJavaFiles", Boolean.toString(state));
  }

  public PsiElementListCellRenderer getListCellRenderer() {
    return new GotoFileCellRenderer(myMaxSize);
  }

  @Nullable
  public String getFullName(final Object element) {
    if (element instanceof PsiFile) {
      final VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
      return virtualFile != null ? virtualFile.getPath() : null;
    }

    return getElementName(element);
  }

  @NotNull
  public String[] getSeparators() {
    return new String[] {"/", "\\"};
  }
}