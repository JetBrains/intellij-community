// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.ComboBoxWithWidePopup;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.panel.PanelGridBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UI;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

/**
 * @author Pavel.Dolgov
 */
public class CreateServiceInterfaceOrClassFix extends CreateServiceClassFixBase {

  private String myInterfaceName;

  public CreateServiceInterfaceOrClassFix(PsiJavaCodeReferenceElement referenceElement) {
    referenceElement = findTopmostReference(referenceElement);
    PsiElement parent = referenceElement.getParent();

    if (parent instanceof PsiUsesStatement && ((PsiUsesStatement)parent).getClassReference() == referenceElement ||
        parent instanceof PsiProvidesStatement && ((PsiProvidesStatement)parent).getInterfaceReference() == referenceElement) {
      if (referenceElement.isQualified()) {
        myInterfaceName = referenceElement.getQualifiedName();
      }
    }
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("create.service.interface.fix.name", myInterfaceName);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("create.service.interface.fix.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (myInterfaceName == null) {
      return false;
    }
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);

    return psiFacade.findClass(myInterfaceName, projectScope) == null &&
           isQualifierInProject(myInterfaceName, project);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    String qualifierText = StringUtil.getPackageName(myInterfaceName);
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);

    PsiClass outerClass = psiFacade.findClass(qualifierText, GlobalSearchScope.projectScope(project));
    if (outerClass != null) {
      createClassInOuter(qualifierText, outerClass);
      return;
    }

    PsiPackage psiPackage;
    do {
      psiPackage = psiFacade.findPackage(qualifierText);
      qualifierText = StringUtil.getPackageName(qualifierText);
    }
    while (psiPackage == null && !StringUtil.isEmpty(qualifierText));

    if (psiPackage != null) {
      Map<Module, PsiDirectory[]> psiRootDirs = getModuleRootDirs(psiPackage);
      if (!psiRootDirs.isEmpty()) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          PsiDirectory rootDir = file.getUserData(SERVICE_ROOT_DIR);
          CreateClassKind classKind = file.getUserData(SERVICE_CLASS_KIND);
          if (rootDir != null && classKind != null) {
            WriteAction.run(() -> createClassInRoot(myInterfaceName, classKind, rootDir, file, null));
          }
          return;
        }
        CreateServiceInterfaceDialog dialog = new CreateServiceInterfaceDialog(project, psiRootDirs);
        if (dialog.showAndGet()) {
          PsiDirectory rootDir = dialog.getRootDir();
          if (rootDir != null) {
            CreateClassKind classKind = dialog.getClassKind();
            PsiClass psiClass = WriteAction.compute(() -> createClassInRoot(myInterfaceName, classKind, rootDir, file, null));
            positionCursor(psiClass);
          }
        }
      }
    }
  }

  @NotNull
  private static Map<Module, PsiDirectory[]> getModuleRootDirs(@NotNull PsiPackage psiPackage) {
    ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(psiPackage.getProject());
    return StreamEx.of(psiPackage.getDirectories())
      .map(PsiDirectory::getVirtualFile)
      .map(index::getSourceRootForFile)
      .nonNull()
      .map(index::getModuleForFile)
      .nonNull()
      .distinct()
      .mapToEntry(CreateServiceClassFixBase::getModuleRootDirs)
      .filterValues(dirs -> !ArrayUtil.isEmpty(dirs))
      .toMap();
  }

  private void createClassInOuter(@NotNull String qualifierText, @NotNull PsiClass outerClass) {
    String name = myInterfaceName.substring(qualifierText.length() + 1);
    PsiClass psiClass = WriteAction.compute(() -> createClassInOuterImpl(name, outerClass, null));
    positionCursor(psiClass);
  }

  private class CreateServiceInterfaceDialog extends DialogWrapper {
    private final ComboBoxWithWidePopup<Module> myModuleCombo = new ComboBoxWithWidePopup<>();
    private final ComboBoxWithWidePopup<PsiDirectory> myRootDirCombo = new ComboBoxWithWidePopup<>();
    private final TemplateKindCombo myKindCombo = new TemplateKindCombo();

    protected CreateServiceInterfaceDialog(@Nullable Project project, @NotNull Map<Module, PsiDirectory[]> psiRootDirs) {
      super(project);
      setTitle("Create Service");

      myModuleCombo.setRenderer(new ListCellRendererWrapper<Module>() {
        @Override
        public void customize(JList list, Module module, int index, boolean selected, boolean hasFocus) {
          setText(module.getName());
        }
      });

      myRootDirCombo.setRenderer(new PsiDirectoryListCellRenderer());
      myModuleCombo.addActionListener(e -> updateRootDirsCombo(psiRootDirs));
      Module[] modules = psiRootDirs.keySet().toArray(Module.EMPTY_ARRAY);
      Arrays.sort(modules, Comparator.comparing(Module::getName));
      myModuleCombo.setModel(new DefaultComboBoxModel<>(modules));
      updateRootDirsCombo(psiRootDirs);


      myKindCombo.addItem(CommonRefactoringUtil.capitalize(CreateClassKind.CLASS.getDescription()), PlatformIcons.CLASS_ICON,
                          CreateClassKind.CLASS.name());
      myKindCombo.addItem(CommonRefactoringUtil.capitalize(CreateClassKind.INTERFACE.getDescription()), PlatformIcons.INTERFACE_ICON,
                          CreateClassKind.INTERFACE.name());
      myKindCombo.addItem(CommonRefactoringUtil.capitalize(CreateClassKind.ANNOTATION.getDescription()), PlatformIcons.ANNOTATION_TYPE_ICON,
                          CreateClassKind.ANNOTATION.name());

      init();
    }

    private void updateRootDirsCombo(@NotNull Map<Module, PsiDirectory[]> psiRootDirs) {
      Module module = (Module)myModuleCombo.getSelectedItem();
      PsiDirectory[] moduleRootDirs = psiRootDirs.getOrDefault(module, PsiDirectory.EMPTY_ARRAY);
      myRootDirCombo.setModel(new DefaultComboBoxModel<>(moduleRootDirs));
    }

    @NotNull
    @Override
    protected Action[] createActions() {
      return new Action[]{getOKAction(), getCancelAction()};
    }

    @Override
    protected JComponent createCenterPanel() {
      return null;
    }

    @Nullable
    @Override
    protected JComponent createNorthPanel() {
      JTextField nameTextField = new JTextField(myInterfaceName);
      nameTextField.setEditable(false);
      PanelGridBuilder builder = UI.PanelFactory.grid();
      builder.add(UI.PanelFactory.panel(nameTextField).withLabel("Name:"));
      if (myModuleCombo.getModel().getSize() > 1) builder.add(UI.PanelFactory.panel(myModuleCombo).withLabel("Module:"));
      if (myRootDirCombo.getModel().getSize() > 1) builder.add(UI.PanelFactory.panel(myRootDirCombo).withLabel("Source root:"));
      builder.add(UI.PanelFactory.panel(myKindCombo).withLabel("Kind:"));
      return builder.createPanel();
    }

    @Nullable
    public PsiDirectory getRootDir() {
      return (PsiDirectory)myRootDirCombo.getSelectedItem();
    }

    public CreateClassKind getClassKind() {
      return CreateClassKind.valueOf(myKindCombo.getSelectedName());
    }
  }
}
