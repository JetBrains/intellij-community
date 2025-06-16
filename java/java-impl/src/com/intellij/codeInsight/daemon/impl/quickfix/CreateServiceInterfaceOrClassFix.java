// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.ComboBoxWithWidePopup;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.panel.PanelGridBuilder;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.IconManager;
import com.intellij.ui.SimpleListCellRenderer;
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

public class CreateServiceInterfaceOrClassFix extends CreateServiceClassFixBase {
  private @NlsSafe String myInterfaceName;

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

  @Override
  public @Nls @NotNull String getText() {
    return QuickFixBundle.message("create.service.interface.fix.name", myInterfaceName);
  }

  @Override
  public @Nls @NotNull String getFamilyName() {
    return QuickFixBundle.message("create.service.interface.fix.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (myInterfaceName == null) return false;
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
    return psiFacade.findClass(myInterfaceName, projectScope) == null &&
           isQualifierInProject(myInterfaceName, project);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
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
          PsiDirectory rootDir = psiFile.getUserData(SERVICE_ROOT_DIR);
          CreateClassKind classKind = psiFile.getUserData(SERVICE_CLASS_KIND);
          if (rootDir != null && classKind != null) {
            WriteAction.run(() -> createClassInRoot(myInterfaceName, classKind, rootDir, psiFile, null));
          }
          return;
        }
        CreateServiceInterfaceDialog dialog = new CreateServiceInterfaceDialog(project, psiRootDirs);
        if (dialog.showAndGet()) {
          PsiDirectory rootDir = dialog.getRootDir();
          if (rootDir != null) {
            CreateClassKind classKind = dialog.getClassKind();
            PsiClass psiClass = WriteAction.compute(() -> createClassInRoot(myInterfaceName, classKind, rootDir, psiFile, null));
            positionCursor(psiClass);
          }
        }
      }
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, "", "public interface " + myInterfaceName + " {}");
  }

  private static @NotNull Map<Module, PsiDirectory[]> getModuleRootDirs(@NotNull PsiPackage psiPackage) {
    ProjectFileIndex index = ProjectFileIndex.getInstance(psiPackage.getProject());
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
      setTitle(QuickFixBundle.message("create.service"));

      myModuleCombo.setRenderer(SimpleListCellRenderer.create("", Module::getName));

      myRootDirCombo.setRenderer(new PsiDirectoryListCellRenderer());
      myModuleCombo.addActionListener(e -> updateRootDirsCombo(psiRootDirs));
      Module[] modules = psiRootDirs.keySet().toArray(Module.EMPTY_ARRAY);
      Arrays.sort(modules, Comparator.comparing(Module::getName));
      myModuleCombo.setModel(new DefaultComboBoxModel<>(modules));
      updateRootDirsCombo(psiRootDirs);

      myKindCombo.addItem(StringUtil.capitalize(CreateClassKind.CLASS.getDescription()),
                          IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Class), CreateClassKind.CLASS.name());
      myKindCombo.addItem(StringUtil.capitalize(CreateClassKind.INTERFACE.getDescription()), PlatformIcons.INTERFACE_ICON, CreateClassKind.INTERFACE.name());
      myKindCombo.addItem(StringUtil.capitalize(CreateClassKind.ANNOTATION.getDescription()), PlatformIcons.ANNOTATION_TYPE_ICON, CreateClassKind.ANNOTATION.name());

      init();
    }

    private void updateRootDirsCombo(@NotNull Map<Module, PsiDirectory[]> psiRootDirs) {
      Module module = (Module)myModuleCombo.getSelectedItem();
      PsiDirectory[] moduleRootDirs = psiRootDirs.getOrDefault(module, PsiDirectory.EMPTY_ARRAY);
      myRootDirCombo.setModel(new DefaultComboBoxModel<>(moduleRootDirs));
    }

    @Override
    protected Action @NotNull [] createActions() {
      return new Action[]{getOKAction(), getCancelAction()};
    }

    @Override
    protected JComponent createCenterPanel() {
      return null;
    }

    @Override
    protected @Nullable JComponent createNorthPanel() {
      JTextField nameTextField = new JTextField(myInterfaceName);
      nameTextField.setEditable(false);
      PanelGridBuilder builder = UI.PanelFactory.grid();
      builder.add(UI.PanelFactory.panel(nameTextField).withLabel(CommonBundle.message("label.name") + ":"));
      if (myModuleCombo.getModel().getSize() > 1) {
        builder.add(UI.PanelFactory.panel(myModuleCombo).withLabel(CommonBundle.message("label.module") + ":"));
      }
      if (myRootDirCombo.getModel().getSize() > 1) {
        builder.add(UI.PanelFactory.panel(myRootDirCombo).withLabel(CommonBundle.message("label.source.root") + ":"));
      }
      builder.add(UI.PanelFactory.panel(myKindCombo).withLabel(CommonBundle.message("label.kind") + ":"));
      return builder.createPanel();
    }

    public @Nullable PsiDirectory getRootDir() {
      return (PsiDirectory)myRootDirCombo.getSelectedItem();
    }

    public CreateClassKind getClassKind() {
      return CreateClassKind.valueOf(myKindCombo.getSelectedName());
    }
  }
}