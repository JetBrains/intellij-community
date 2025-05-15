// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.module.impl.scopes.ModulesScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBoxWithWidePopup;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.panel.PanelGridBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Optional;

public class CreateServiceImplementationClassFix extends CreateServiceClassFixBase {
  private String mySuperClassName;
  private String myImplementationClassName;
  private String myModuleName;
  private boolean myInterface;

  public CreateServiceImplementationClassFix(PsiJavaCodeReferenceElement referenceElement) {
    init(referenceElement);
  }

  private void init(@NotNull PsiJavaCodeReferenceElement referenceElement) {
    referenceElement = findTopmostReference(referenceElement);
    PsiElement parent = referenceElement.getParent();

    if (parent != null && referenceElement.isQualified()) {
      PsiProvidesStatement providesStatement = ObjectUtils.tryCast(parent.getParent(), PsiProvidesStatement.class);
      if (providesStatement != null && providesStatement.getImplementationList() == parent) {
        myImplementationClassName = referenceElement.getQualifiedName();
        if (myImplementationClassName != null) {
          PsiJavaCodeReferenceElement interfaceReference = providesStatement.getInterfaceReference();
          if (interfaceReference != null) {
            PsiClass superClass = ObjectUtils.tryCast(interfaceReference.resolve(), PsiClass.class);
            if (superClass != null) {
              myInterface = superClass.isInterface();
              mySuperClassName = superClass.getQualifiedName();
              if (mySuperClassName != null) {
                myModuleName = Optional.of(referenceElement)
                  .map(PsiElement::getContainingFile)
                  .map(ModuleUtilCore::findModuleForFile)
                  .map(Module::getName)
                  .orElse(null);
              }
            }
          }
        }
      }
    }
  }

  @Override
  public @Nls @NotNull String getText() {
    return QuickFixBundle.message("create.service.implementation.fix.name", myImplementationClassName);
  }

  @Override
  public @Nls @NotNull String getFamilyName() {
    return QuickFixBundle.message("create.service.implementation.fix.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (mySuperClassName != null && myImplementationClassName != null && myModuleName != null) {
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
      return psiFacade.findClass(myImplementationClassName, projectScope) == null &&
             psiFacade.findClass(mySuperClassName, projectScope) != null &&
             isQualifierInProject(myImplementationClassName, project);
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    Module module = ModuleManager.getInstance(project).findModuleByName(myModuleName);
    if (module != null) {
      String qualifierText = StringUtil.getPackageName(myImplementationClassName);
      if (!StringUtil.isEmpty(qualifierText)) {
        PsiClass outerClass = findClassInModule(qualifierText, module);
        if (outerClass != null) {
          createClassInOuter(qualifierText, outerClass);
          return;
        }
      }

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        PsiDirectory rootDir = psiFile.getUserData(SERVICE_ROOT_DIR);
        Boolean isSubclass = psiFile.getUserData(SERVICE_IS_SUBCLASS);
        if (rootDir != null && isSubclass != null) {
          WriteAction.run(() -> createClassInRoot(rootDir, isSubclass, psiFile));
        }
        return;
      }
      PsiDirectory[] psiRootDirs = getModuleRootDirs(module);

      CreateServiceImplementationDialog dialog = new CreateServiceImplementationDialog(project, psiRootDirs, mySuperClassName);
      if (dialog.showAndGet()) {
        PsiDirectory psiRootDir = dialog.getRootDir();
        if (psiRootDir != null) {
          boolean isSubclass = dialog.isSubclass();
          PsiClass psiClass = WriteAction.compute(() -> createClassInRoot(psiRootDir, isSubclass, psiFile));
          positionCursor(psiClass);
        }
      }
    }
  }

  private PsiClass createClassInRoot(@NotNull PsiDirectory psiRootDir, boolean isSubclass, @NotNull PsiElement contextElement) {
    Project project = psiRootDir.getProject();
    PsiClass psiImplClass = createClassInRoot(myImplementationClassName, CreateClassKind.CLASS,
                                              psiRootDir, contextElement, isSubclass ? mySuperClassName : null);
    if (psiImplClass != null && !isSubclass) {
      String text = "public static " + mySuperClassName + " provider() { return null;}";
      PsiMethod method = JavaPsiFacade.getElementFactory(project).createMethodFromText(text, psiImplClass.getLBrace());
      method = (PsiMethod)psiImplClass.addAfter(method, psiImplClass.getLBrace());
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(method);
    }
    return psiImplClass;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    String superClassName = StringUtil.getShortName(mySuperClassName);
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, "",
                                               "public class " + StringUtil.getShortName(myImplementationClassName) + " " +
                                               (myInterface ? JavaKeywords.IMPLEMENTS : JavaKeywords.EXTENDS) + " " + superClassName + " {\n" +
                                               "  public static " + superClassName + " provider() { return null;}" +
                                               "\n}");
  }

  private static @Nullable PsiClass findClassInModule(@NotNull String className, @NotNull Module module) {
    Project project = module.getProject();
    ModulesScope scope = new ModulesScope(Collections.singleton(module), project);
    return JavaPsiFacade.getInstance(project).findClass(className, scope);
  }

  private void createClassInOuter(String qualifierText, PsiClass outerClass) {
    String name = myImplementationClassName.substring(qualifierText.length() + 1);
    PsiClass psiClass = WriteAction.compute(() -> createClassInOuterImpl(name, outerClass, mySuperClassName));
    positionCursor(psiClass);
  }

  private static class CreateServiceImplementationDialog extends DialogWrapper {
    private final ComboBoxWithWidePopup<PsiDirectory> myRootDirCombo = new ComboBoxWithWidePopup<>();
    private final JRadioButton mySubclassButton = new JBRadioButton();
    private final JRadioButton myProviderButton = new JBRadioButton();

    protected CreateServiceImplementationDialog(@Nullable Project project,
                                                PsiDirectory @NotNull [] psiRootDirs,
                                                @NotNull String superClassName) {
      super(project);
      setTitle(QuickFixBundle.message("create.service.implementation"));

      mySubclassButton.setText(JavaBundle.message("radio.button.subclass.of.0", superClassName));
      mySubclassButton.setSelected(true);
      myProviderButton.setText(JavaBundle.message("radio.button.with.provider.method"));

      ButtonGroup group = new ButtonGroup();
      group.add(mySubclassButton);
      group.add(myProviderButton);

      myRootDirCombo.setRenderer(new PsiDirectoryListCellRenderer());
      myRootDirCombo.setModel(new DefaultComboBoxModel<>(psiRootDirs));

      init();
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
      PanelGridBuilder builder = UI.PanelFactory.grid();
      builder.add(UI.PanelFactory.panel(mySubclassButton).withLabel(JavaBundle.message("label.implementation")))
             .add(UI.PanelFactory.panel(myProviderButton));
      if (myRootDirCombo.getModel().getSize() > 1) {
        builder.add(UI.PanelFactory.panel(myRootDirCombo).withLabel(CommonBundle.message("label.source.root") + ":"));
      }
      return builder.createPanel();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
      return mySubclassButton;
    }

    public @Nullable PsiDirectory getRootDir() {
      return (PsiDirectory)myRootDirCombo.getSelectedItem();
    }

    public boolean isSubclass() {
      return mySubclassButton.isSelected();
    }
  }
}
