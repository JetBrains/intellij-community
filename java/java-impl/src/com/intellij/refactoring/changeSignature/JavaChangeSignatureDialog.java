/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.inCallers.JavaCallerChooser;
import com.intellij.refactoring.ui.CodeFragmentTableCellRenderer;
import com.intellij.refactoring.ui.JavaCodeFragmentTableCellEditor;
import com.intellij.refactoring.ui.JavaVisibilityPanel;
import com.intellij.refactoring.ui.VisibilityPanelBase;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.ui.EditableRowTable;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Set;

public class JavaChangeSignatureDialog extends ChangeSignatureDialogBase<ParameterInfoImpl, PsiMethod, JavaMethodDescriptor> {

  private ExceptionsTableModel myExceptionsTableModel;
  protected Set<PsiMethod> myMethodsToPropagateExceptions;
  private JButton myPropagateExnChangesButton;
  private Tree myExceptionPropagationTree;

  public JavaChangeSignatureDialog(Project project,
                                   final PsiMethod method,
                                   boolean allowDelegation,
                                   PsiElement defaultValueContext) {
    this(project, new JavaMethodDescriptor(method), allowDelegation, defaultValueContext);
  }

  protected JavaChangeSignatureDialog(Project project,
                                      final JavaMethodDescriptor methodDescriptor,
                                      boolean allowDelegation,
                                      PsiElement defaultValueContext) {
    super(project, methodDescriptor, allowDelegation, defaultValueContext);
  }

  @Override
  protected JComponent createNorthPanel() {
    JComponent c = super.createNorthPanel();
    myPropagateExnChangesButton = new JButton(RefactoringBundle.message("changeSignature.propagate.exceptions.title"));
    myPropagateExnChangesButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Ref<JavaCallerChooser> chooser = new Ref<JavaCallerChooser>();
        Consumer<Set<PsiMethod>> callback = new Consumer<Set<PsiMethod>>() {
          @Override
          public void consume(Set<PsiMethod> psiMethods) {
            myMethodsToPropagateExceptions = psiMethods;
            myExceptionPropagationTree = chooser.get().getTree();
          }
        };
        chooser.set(
          new JavaCallerChooser(myMethod.getMethod(), myProject, RefactoringBundle.message("changeSignature.exception.caller.chooser"),
                                myExceptionPropagationTree, callback));
        chooser.get().show();
      }
    });
    myPropagatePanel.add(myPropagateExnChangesButton);
    return c;
  }

  @Override
  protected VisibilityPanelBase createVisibilityControl() {
    return new JavaVisibilityPanel(false, false);
  }

  @Override
  protected void updatePropagateButtons() {
    super.updatePropagateButtons();
    myPropagateExnChangesButton.setEnabled(!isGenerateDelegate() && mayPropagateExceptions());
  }

  private boolean mayPropagateExceptions() {
    final ThrownExceptionInfo[] thrownExceptions = myExceptionsTableModel.getThrownExceptions();
    final PsiClassType[] types = myMethod.getMethod().getThrowsList().getReferencedTypes();
    if (thrownExceptions.length <= types.length) return false;
    for (int i = 0; i < types.length; i++) {
      if (thrownExceptions[i].getOldIndex() != i) return false;
    }
    return true;
  }

  @Override
  protected JPanel createAdditionalPanel() {
    // this method is invoked before constuctor body
    myExceptionsTableModel = new ExceptionsTableModel(myMethod.getMethod().getThrowsList());
    myExceptionsTableModel.setTypeInfos(myMethod.getMethod());

    JBTable exceptionsTable = new JBTable(myExceptionsTableModel);
    exceptionsTable.getColumnModel().getColumn(0).setCellRenderer(new CodeFragmentTableCellRenderer(myProject));
    exceptionsTable.getColumnModel().getColumn(0).setCellEditor(new JavaCodeFragmentTableCellEditor(myProject));
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder(RefactoringBundle.message("changeSignature.exceptions.panel.border.title")));

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(exceptionsTable);

    JPanel tablePanel = new JPanel(new BorderLayout());
    tablePanel.add(scrollPane, BorderLayout.CENTER);

    tablePanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    panel.add(tablePanel, BorderLayout.CENTER);

    exceptionsTable.setPreferredScrollableViewportSize(new Dimension(450, exceptionsTable.getRowHeight() * 8));
    exceptionsTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    exceptionsTable.getSelectionModel().setSelectionInterval(0, 0);
    exceptionsTable.setSurrendersFocusOnKeystroke(true);

    JPanel buttonsPanel = EditableRowTable.createButtonsTable(exceptionsTable, myExceptionsTableModel, false);

    panel.add(buttonsPanel, BorderLayout.EAST);

    myExceptionsTableModel.addTableModelListener(
      new TableModelListener() {
        public void tableChanged(TableModelEvent e) {
          JavaChangeSignatureDialog.this.updateSignature();
        }
      }
    );

    return panel;
  }

  @Override
  protected LanguageFileType getFileType() {
    return StdFileTypes.JAVA;
  }

  @Override
  protected ParameterTableModelBase<ParameterInfoImpl> createParametersInfoModel(MethodDescriptor<ParameterInfoImpl> method) {
    return new JavaParameterTableModel(((JavaMethodDescriptor)method).getMethod().getParameterList(), myDefaultValueContext, this);
  }

  @Override
  protected void invokeRefactoring(BaseRefactoringProcessor processor) {
    if (myMethodsToPropagateExceptions != null && !mayPropagateExceptions()) {
      Messages.showWarningDialog(myProject, RefactoringBundle.message("changeSignature.exceptions.wont.propagate"),
                                 ChangeSignatureHandler.REFACTORING_NAME);
      myMethodsToPropagateExceptions = null;
    }
    super.invokeRefactoring(processor);
  }

  @Override
  protected BaseRefactoringProcessor createRefactoringProcessor() {
    List<ParameterInfoImpl> parameters = getParameters();
    return new ChangeSignatureProcessor(myProject, myMethod.getMethod(), isGenerateDelegate(),
                                        getVisibility(), getMethodName(), getReturnType(),
                                        parameters.toArray(new ParameterInfoImpl[parameters.size()]), getExceptions(),
                                        myMethodsToPropagateParameters,
                                        myMethodsToPropagateExceptions);
  }

  @Nullable
  protected CanonicalTypes.Type getReturnType() {
    if (myReturnTypeField != null) {
      try {
        final PsiType type = ((PsiTypeCodeFragment)myReturnTypeCodeFragment).getType();
        return CanonicalTypes.createTypeWrapper(type);
      }
      catch (PsiTypeCodeFragment.TypeSyntaxException e) {
        return null;
      }
      catch (PsiTypeCodeFragment.NoTypeException e) {
        return null;
      }
    }

    return null;
  }

  protected ThrownExceptionInfo[] getExceptions() {
    return myExceptionsTableModel.getThrownExceptions();
  }

  @Override
  protected PsiCodeFragment createReturnTypeCodeFragment() {
    String returnTypeText = StringUtil.notNullize(myMethod.getReturnTypeText());
    return JavaPsiFacade.getInstance(myProject).getElementFactory()
      .createTypeCodeFragment(returnTypeText, myMethod.getMethod(), true, true);
  }

  @Override
  protected CallerChooserBase<PsiMethod> createCallerChooser(String title, Tree treeToReuse, Consumer<Set<PsiMethod>> callback) {
    return new JavaCallerChooser(myMethod.getMethod(), myProject, title, treeToReuse, callback);
  }

  @Override
  protected String validateAndCommitData() {
    PsiManager manager = PsiManager.getInstance(myProject);
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

    String name = getMethodName();
    if (!JavaPsiFacade.getInstance(manager.getProject()).getNameHelper().isIdentifier(name)) {
      return RefactoringMessageUtil.getIncorrectIdentifierMessage(name);
    }

    if (!myMethod.isConstructor()) {
      try {
        ((PsiTypeCodeFragment)myReturnTypeCodeFragment).getType();
      }
      catch (PsiTypeCodeFragment.TypeSyntaxException e) {
        myReturnTypeField.requestFocus();
        return RefactoringBundle.message("changeSignature.wrong.return.type", myReturnTypeCodeFragment.getText());
      }
      catch (PsiTypeCodeFragment.NoTypeException e) {
        myReturnTypeField.requestFocus();
        return RefactoringBundle.message("changeSignature.no.return.type");
      }
    }

    List<ParameterTableModelItemBase<ParameterInfoImpl>> parameterInfos = myParametersTableModel.getItems();
    final int newParametersNumber = parameterInfos.size();

    for (int i = 0; i < newParametersNumber; i++) {
      ParameterTableModelItemBase<ParameterInfoImpl> item = parameterInfos.get(i);

      if (!JavaPsiFacade.getInstance(manager.getProject()).getNameHelper().isIdentifier(item.parameter.getName())) {
        return RefactoringMessageUtil.getIncorrectIdentifierMessage(item.parameter.getName());
      }

      final PsiType type;
      try {
        type = ((PsiTypeCodeFragment)parameterInfos.get(i).typeCodeFragment).getType();
      }
      catch (PsiTypeCodeFragment.TypeSyntaxException e) {
        return RefactoringBundle
          .message("changeSignature.wrong.type.for.parameter", item.typeCodeFragment.getText(), item.parameter.getName());
      }
      catch (PsiTypeCodeFragment.NoTypeException e) {
        return RefactoringBundle.message("changeSignature.no.type.for.parameter", item.parameter.getName());
      }

      item.parameter.setType(type);

      if (type instanceof PsiEllipsisType && i != newParametersNumber - 1) {
        return RefactoringBundle.message("changeSignature.vararg.not.last");
      }

      if (item.parameter.oldParameterIndex < 0) {
        item.parameter.defaultValue = item.defaultValueCodeFragment.getText();
        String def = item.parameter.defaultValue;
        def = def.trim();
        if (!(type instanceof PsiEllipsisType)) {
          if (def.length() == 0) {
            return RefactoringBundle.message("changeSignature.no.default.value", item.parameter.getName());
          }

          try {
            factory.createExpressionFromText(def, null);
          }
          catch (IncorrectOperationException e) {
            return e.getMessage();
          }
        }
      }
    }

    ThrownExceptionInfo[] exceptionInfos = myExceptionsTableModel.getThrownExceptions();
    PsiTypeCodeFragment[] typeCodeFragments = myExceptionsTableModel.getTypeCodeFragments();
    for (int i = 0; i < exceptionInfos.length; i++) {
      ThrownExceptionInfo exceptionInfo = exceptionInfos[i];
      PsiTypeCodeFragment typeCodeFragment = typeCodeFragments[i];
      try {
        PsiType type = typeCodeFragment.getType();
        if (!(type instanceof PsiClassType)) {
          return RefactoringBundle.message("changeSignature.wrong.type.for.exception", typeCodeFragment.getText());
        }

        PsiClassType throwable = JavaPsiFacade.getInstance(myProject).getElementFactory()
          .createTypeByFQClassName("java.lang.Throwable", type.getResolveScope());
        if (!throwable.isAssignableFrom(type)) {
          return RefactoringBundle.message("changeSignature.not.throwable.type", typeCodeFragment.getText());
        }
        exceptionInfo.setType((PsiClassType)type);
      }
      catch (PsiTypeCodeFragment.TypeSyntaxException e) {
        return RefactoringBundle.message("changeSignature.wrong.type.for.exception", typeCodeFragment.getText());
      }
      catch (PsiTypeCodeFragment.NoTypeException e) {
        return RefactoringBundle.message("changeSignature.no.type.for.exception");
      }
    }

    // warnings
    try {
      if (!myMethod.isConstructor()) {
        if (!RefactoringUtil.isResolvableType(((PsiTypeCodeFragment)myReturnTypeCodeFragment).getType())) {
          if (Messages.showOkCancelDialog(myProject, RefactoringBundle
            .message("changeSignature.cannot.resolve.return.type", myReturnTypeCodeFragment.getText()),
                                          RefactoringBundle.message("changeSignature.refactoring.name"), Messages.getWarningIcon()) != 0) {
            return EXIT_SILENTLY;
          }
        }
      }
      for (ParameterTableModelItemBase<ParameterInfoImpl> item : parameterInfos) {

        if (!RefactoringUtil.isResolvableType(((PsiTypeCodeFragment)item.typeCodeFragment).getType())) {
          if (Messages.showOkCancelDialog(myProject, RefactoringBundle
            .message("changeSignature.cannot.resolve.parameter.type", item.typeCodeFragment.getText(), item.parameter.getName()),
                                          RefactoringBundle.message("changeSignature.refactoring.name"), Messages.getWarningIcon()) !=
              0) {
            return EXIT_SILENTLY;
          }
        }
      }
    }
    catch (PsiTypeCodeFragment.IncorrectTypeException ignored) {
    }
    return null;
  }

  @Override
  protected boolean isResolvableType(ParameterInfoImpl info, MethodDescriptor<ParameterInfoImpl> method) {
    return RefactoringUtil.isResolvableType(info.createType(myMethod.getMethod(), PsiManager.getInstance(myProject)));
  }

  @Override
  protected String calculateSignature() {
    return doCalculateSignature(myMethod.getMethod());
  }

  protected String doCalculateSignature(PsiMethod method) {
    @NonNls StringBuilder buffer = new StringBuilder();

    PsiModifierList modifierList = method.getModifierList();
    String modifiers = modifierList.getText();
    String oldModifier = VisibilityUtil.getVisibilityModifier(modifierList);
    String newModifier = getVisibility();
    String newModifierStr = VisibilityUtil.getVisibilityString(newModifier);
    if (!newModifier.equals(oldModifier)) {
      int index = modifiers.indexOf(oldModifier);
      if (index >= 0) {
        StringBuilder buf = new StringBuilder(modifiers);
        buf.replace(index,
                    index + oldModifier.length() + ("".equals(newModifierStr) ? 1 : 0),
                    newModifierStr);
        modifiers = buf.toString();
      }
      else {
        if (!"".equals(newModifierStr)) newModifierStr += " ";
        modifiers = newModifierStr + modifiers;
      }
    }

    buffer.append(modifiers);
    if (modifiers.length() > 0 &&
        !StringUtil.endsWithChar(modifiers, '\n') &&
        !StringUtil.endsWithChar(modifiers, '\r') &&
        !StringUtil.endsWithChar(modifiers, ' ')) {
      buffer.append(" ");
    }

    if (!method.isConstructor()) {
      final CanonicalTypes.Type returnType = getReturnType();
      if (returnType != null) {
        buffer.append(returnType.getTypeText());
      }
      buffer.append(" ");
    }
    buffer.append(getMethodName());
    buffer.append("(");

    final String indent = "    ";
    List<ParameterTableModelItemBase<ParameterInfoImpl>> items = myParametersTableModel.getItems();
    for (int i = 0; i < items.size(); i++) {
      ParameterTableModelItemBase<ParameterInfoImpl> item = items.get(i);
      if (i > 0) {
        buffer.append(",");
      }
      buffer.append("\n");
      buffer.append(indent);
      buffer.append(item.typeCodeFragment.getText());
      buffer.append(" ");
      buffer.append(item.parameter.getName());
    }
    if (!items.isEmpty()) {
      buffer.append("\n");
    }
    buffer.append(")");
    PsiTypeCodeFragment[] thrownExceptionsFragments = myExceptionsTableModel.getTypeCodeFragments();
    if (thrownExceptionsFragments.length > 0) {
      buffer.append("\n");
      buffer.append("throws\n");
      for (int i = 0; i < thrownExceptionsFragments.length; i++) {
        String text = thrownExceptionsFragments[i].getText();
        buffer.append(indent);
        buffer.append(text);
        if (i < thrownExceptionsFragments.length - 1) {
          buffer.append(",");
        }
        buffer.append("\n");
      }
    }
    return buffer.toString();
  }

}
