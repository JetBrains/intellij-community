/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateServiceClassFixBase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.ui.ComboBoxWithWidePopup;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.panel.JBPanelFactory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * @author Pavel.Dolgov
 */
public class CreateClassInPackageInModuleFix implements IntentionAction {
  public static final Key<Boolean> IS_INTERFACE = Key.create("CREATE_CLASS_IN_PACKAGE_IS_INTERFACE");
  public static final Key<PsiDirectory> ROOT_DIR = Key.create("CREATE_CLASS_IN_PACKAGE_ROOT_DIR");
  public static final Key<String> NAME = Key.create("CREATE_CLASS_IN_PACKAGE_NAME");

  private final String myModuleName;
  private final String myPackageName;

  public CreateClassInPackageInModuleFix(String moduleName, String packageName) {
    myModuleName = moduleName;
    myPackageName = packageName;
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return "Create a class in '" + myPackageName + "'";
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return "Create a class in package";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return ModuleManager.getInstance(project).findModuleByName(myModuleName) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      Boolean isInterface = IS_INTERFACE.get(file);
      PsiDirectory rootDir = ROOT_DIR.get(file);
      String name = NAME.get(file);
      if (isInterface != null && rootDir != null && name != null) {
        WriteAction.run(() -> createClassInPackage(isInterface ? CreateClassKind.INTERFACE : CreateClassKind.CLASS, rootDir, name, file));
      }
      return;
    }
    Module module = ModuleManager.getInstance(project).findModuleByName(myModuleName);
    if (module != null) {
      List<VirtualFile> roots = new ArrayList<>();
      JavaProjectRootsUtil.collectSuitableDestinationSourceRoots(module, roots);
      PsiManager psiManager = PsiManager.getInstance(project);
      PsiDirectory[] rootDirs = roots.stream()
        .sorted(Comparator.comparing(VirtualFile::getPresentableUrl))
        .map(psiManager::findDirectory)
        .filter(Objects::nonNull)
        .toArray(PsiDirectory[]::new);

      if (rootDirs.length != 0) {
        CreateClassInPackageDialog dialog = new CreateClassInPackageDialog(project, rootDirs);
        if (dialog.showAndGet()) {
          CreateClassKind kind = dialog.getKind();
          PsiDirectory rootDir = dialog.getRootDir();
          String name = dialog.getName();
          if (rootDir != null) {
            PsiClass psiClass = WriteAction.compute(() -> createClassInPackage(kind, rootDir, name, file));
            CreateServiceClassFixBase.positionCursor(psiClass);
          }
        }
      }
    }
  }

  @Nullable
  private PsiClass createClassInPackage(@NotNull CreateClassKind kind,
                                        @NotNull PsiDirectory rootDir,
                                        @NotNull String name,
                                        @NotNull PsiElement contextElement) {
    PsiDirectory psiPackageDir = CreateServiceClassFixBase.getOrCreatePackageDirInRoot(myPackageName, rootDir);
    if (psiPackageDir != null) {
      return CreateFromUsageUtils.createClass(kind, psiPackageDir, name, contextElement.getManager(), contextElement, null, null);
    }
    return null;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Nullable
  public static IntentionAction createFix(@NotNull Module module, @Nullable String packageName) {
    return StringUtil.isNotEmpty(packageName) ? new CreateClassInPackageInModuleFix(module.getName(), packageName) : null;
  }

  private class CreateClassInPackageDialog extends DialogWrapper {
    private final JBTextField myNameTextField = new JBTextField();
    private final ComboBoxWithWidePopup<PsiDirectory> myRootDirCombo = new ComboBoxWithWidePopup<>();
    private final TemplateKindCombo myKindCombo = new TemplateKindCombo();
    @Nullable private Project myProject;

    protected CreateClassInPackageDialog(@Nullable Project project, @NotNull PsiDirectory[] rootDirs) {
      super(project);
      myProject = project;
      setTitle("Create Class in Package");

      myRootDirCombo.setRenderer(new CreateServiceClassFixBase.PsiDirectoryListCellRenderer());
      myRootDirCombo.setModel(new DefaultComboBoxModel<>(rootDirs));

      for (CreateClassKind kind : CreateClassKind.values()) {
        myKindCombo.addItem(CommonRefactoringUtil.capitalize(kind.getDescription()), getKindIcon(kind), kind.name());
      }

      init();
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
    public JComponent getPreferredFocusedComponent() {
      return myNameTextField;
    }

    @Nullable
    @Override
    protected JComponent createNorthPanel() {
      return JBPanelFactory.grid()
        .add(JBPanelFactory.panel(myNameTextField).withLabel("Name:")
               .withComment("The class will be created in the package '" + myPackageName + "'"))
        .add(JBPanelFactory.panel(myRootDirCombo).withLabel("Source root:"))
        .add(JBPanelFactory.panel(myKindCombo).withLabel("Kind:"))
        .createPanel();
    }

    @Nullable
    @Override
    protected ValidationInfo doValidate() {
      String name = getName();
      PsiDirectory rootDir = getRootDir();
      LanguageLevel level = rootDir != null ? PsiUtil.getLanguageLevel(rootDir) : LanguageLevel.HIGHEST;

      if (PsiNameHelper.getInstance(myProject).isIdentifier(name, level)) {
        return null;
      }
      return new ValidationInfo("This is not a valid Java class name", myNameTextField);
    }

    @NotNull
    public String getName() {
      return myNameTextField.getText().trim();
    }

    @Nullable
    public PsiDirectory getRootDir() {
      return (PsiDirectory)myRootDirCombo.getSelectedItem();
    }

    public CreateClassKind getKind() {
      return CreateClassKind.valueOf(myKindCombo.getSelectedName());
    }

    private Icon getKindIcon(@NotNull CreateClassKind kind) {
      switch (kind) {
        case CLASS: return PlatformIcons.CLASS_ICON;
        case INTERFACE: return PlatformIcons.INTERFACE_ICON;
        case ENUM: return PlatformIcons.ENUM_ICON;
        case ANNOTATION: return PlatformIcons.ANNOTATION_TYPE_ICON;
      }
      return null;
    }
  }
}
