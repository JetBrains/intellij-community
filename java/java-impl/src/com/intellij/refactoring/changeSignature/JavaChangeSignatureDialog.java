/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.inCallers.JavaCallerChooser;
import com.intellij.refactoring.ui.CodeFragmentTableCellRenderer;
import com.intellij.refactoring.ui.JavaCodeFragmentTableCellEditor;
import com.intellij.refactoring.ui.JavaComboBoxVisibilityPanel;
import com.intellij.refactoring.ui.VisibilityPanelBase;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.EditableRowTable;
import com.intellij.ui.TableColumnAnimator;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.table.TableView;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumn;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.intellij.refactoring.changeSignature.ChangeSignatureHandler.REFACTORING_NAME;

/**
 * @author Konstantin Bulenkov
 */
public class JavaChangeSignatureDialog extends ChangeSignatureDialogBase<ParameterInfoImpl, PsiMethod, JavaMethodDescriptor> {
  private ExceptionsTableModel myExceptionsModel;
  protected Set<PsiMethod> myMethodsToPropagateExceptions;
  private AnActionButton myPropExceptionsButton;
  private Tree myExceptionPropagationTree;

  public JavaChangeSignatureDialog(Project project, PsiMethod method, boolean allowDelegation, PsiElement context) {
    this(project, new JavaMethodDescriptor(method), allowDelegation, context);
  }

  protected JavaChangeSignatureDialog(Project project, JavaMethodDescriptor descriptor, boolean allowDelegation, PsiElement context) {
    super(project, descriptor, allowDelegation, context);
  }

  @Override
  protected VisibilityPanelBase createVisibilityControl() {
    return new JavaComboBoxVisibilityPanel();
  }

  @Override
  protected JComponent createCenterPanel() {
    final JComponent centerPanel = super.createCenterPanel();
    myPropagateParamChangesButton.setVisible(true);
    return centerPanel;
  }

  @Override
  protected void updatePropagateButtons() {
    super.updatePropagateButtons();
    myPropExceptionsButton.setEnabled(!isGenerateDelegate() && mayPropagateExceptions());
  }

  protected boolean mayPropagateExceptions() {
    final ThrownExceptionInfo[] exceptions = myExceptionsModel.getThrownExceptions();
    final PsiClassType[] types = myMethod.getMethod().getThrowsList().getReferencedTypes();

    if (exceptions.length <= types.length) {
      return false;
    }

    for (int i = 0; i < types.length; i++) {
      if (exceptions[i].getOldIndex() != i) {
        return false;
      }
    }

    return true;
  }

  @NotNull
  protected List<Pair<String,JPanel>> createAdditionalPanels() {
    // this method is invoked before constructor body
    myExceptionsModel = new ExceptionsTableModel(myMethod.getMethod().getThrowsList());
    myExceptionsModel.setTypeInfos(myMethod.getMethod());

    final JBTable table = new JBTable(myExceptionsModel);
    UIUtil.setTableDecorationEnabled(table);
    table.getColumnModel().getColumn(0).setCellRenderer(new CodeFragmentTableCellRenderer(myProject));
    final JavaCodeFragmentTableCellEditor cellEditor = new JavaCodeFragmentTableCellEditor(myProject);
    cellEditor.addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        final int row = table.getSelectedRow();
        final int col = table.getSelectedColumn();
        myExceptionsModel.setValueAt(cellEditor.getCellEditorValue(), row, col);
        updateSignature();
      }
    });
    table.getColumnModel().getColumn(0).setCellEditor(cellEditor);
    table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getSelectionModel().setSelectionInterval(0, 0);
    table.setSurrendersFocusOnKeystroke(true);

    myPropExceptionsButton = new AnActionButton(RefactoringBundle.message("changeSignature.propagate.exceptions.title"), null, PlatformIcons.NEW_EXCEPTION) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final Ref<JavaCallerChooser> chooser = new Ref<JavaCallerChooser>();
        Consumer<Set<PsiMethod>> callback = new Consumer<Set<PsiMethod>>() {
          @Override
          public void consume(Set<PsiMethod> psiMethods) {
            myMethodsToPropagateExceptions = psiMethods;
            myExceptionPropagationTree = chooser.get().getTree();
          }
        };
        chooser.set(new JavaCallerChooser(myMethod.getMethod(),
                                          myProject,
                                          RefactoringBundle.message("changeSignature.exception.caller.chooser"),
                                          myExceptionPropagationTree,
                                          callback));
        chooser.get().show();
      }
    };
    myPropExceptionsButton.setShortcut(KeyboardShortcut.fromString("alt X"));


    final JPanel panel = EditableRowTable.wrapToTableWithButtons(table,
                                                                        myExceptionsModel,
                                                                        new CustomLineBorder(1,0,0,0),
                                                                        myPropExceptionsButton);

    myExceptionsModel.addTableModelListener(new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        JavaChangeSignatureDialog.this.updateSignature();
      }
    });

    final ArrayList<Pair<String, JPanel>> result = new ArrayList<Pair<String, JPanel>>();
    final String message = RefactoringBundle.message("changeSignature.exceptions.panel.border.title");
    result.add(Pair.create(message, panel));
    return result;
  }

  @Override
  protected LanguageFileType getFileType() {
    return StdFileTypes.JAVA;
  }

  @Override
  protected ParameterTableModelBase<ParameterInfoImpl> createParametersInfoModel(MethodDescriptor<ParameterInfoImpl> descriptor) {
    final PsiParameterList parameterList = ((JavaMethodDescriptor)descriptor).getMethod().getParameterList();
    return new JavaParameterTableModel(parameterList, myDefaultValueContext, this);
  }

  @Override
  protected void customizeParametersTable(TableView<ParameterTableModelItemBase<ParameterInfoImpl>> table) {
    final JTable t = table.getComponent();
    final TableColumn defaultValue = t.getColumnModel().getColumn(2);
    final TableColumn varArg = t.getColumnModel().getColumn(3);
    t.removeColumn(defaultValue);
    t.removeColumn(varArg);
    t.getModel().addTableModelListener(new TableModelListener() {
      @Override
      public void tableChanged(TableModelEvent e) {
        if (e.getType() == TableModelEvent.INSERT) {
          t.getModel().removeTableModelListener(this);
          final TableColumnAnimator animator = new TableColumnAnimator(t);
          animator.setStep(48);
          animator.addColumn(defaultValue, (t.getWidth() - 48) / 3);
          animator.addColumn(varArg, 48);
          animator.startAndDoWhenDone(new Runnable() {
            @Override
            public void run() {
              t.editCellAt(t.getRowCount() - 1, 0);
            }
          });
          animator.start();
        }
      }
    });
  }

  @Override
  protected void invokeRefactoring(final BaseRefactoringProcessor processor) {
    if (myMethodsToPropagateExceptions != null && !mayPropagateExceptions()) {
      Messages.showWarningDialog(myProject, RefactoringBundle.message("changeSignature.exceptions.wont.propagate"), REFACTORING_NAME);
      myMethodsToPropagateExceptions = null;
    }
    super.invokeRefactoring(processor);
  }

  @Override
  protected BaseRefactoringProcessor createRefactoringProcessor() {
    final List<ParameterInfoImpl> parameters = getParameters();
    return new ChangeSignatureProcessor(myProject,
                                        myMethod.getMethod(),
                                        isGenerateDelegate(),
                                        getVisibility(),
                                        getMethodName(),
                                        getReturnType(),
                                        parameters.toArray(new ParameterInfoImpl[parameters.size()]),
                                        getExceptions(),
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
    return myExceptionsModel.getThrownExceptions();
  }

  @Override
  protected PsiCodeFragment createReturnTypeCodeFragment() {
    final String returnTypeText = StringUtil.notNullize(myMethod.getReturnTypeText());
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    return elementFactory.createTypeCodeFragment(returnTypeText, myMethod.getMethod(), true, PsiElementFactory.ALLOW_VOID);
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

    if (myMethod.canChangeReturnType() == MethodDescriptor.ReadWriteOption.ReadWrite) {
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
      } catch (PsiTypeCodeFragment.TypeSyntaxException e) {
        return RefactoringBundle.message("changeSignature.wrong.type.for.parameter",
                                         item.typeCodeFragment.getText(),
                                         item.parameter.getName());
      } catch (PsiTypeCodeFragment.NoTypeException e) {
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

    ThrownExceptionInfo[] exceptionInfos = myExceptionsModel.getThrownExceptions();
    PsiTypeCodeFragment[] typeCodeFragments = myExceptionsModel.getTypeCodeFragments();
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
      if (myMethod.canChangeReturnType() == MethodDescriptor.ReadWriteOption.ReadWrite) {
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
  protected String calculateSignature() {
    return doCalculateSignature(myMethod.getMethod());
  }

  protected String doCalculateSignature(PsiMethod method) {
    final StringBuilder buffer = new StringBuilder();
    final PsiModifierList modifierList = method.getModifierList();
    String modifiers = modifierList.getText();
    final String oldModifier = VisibilityUtil.getVisibilityModifier(modifierList);
    final String newModifier = getVisibility();
    String newModifierStr = VisibilityUtil.getVisibilityString(newModifier);
    if (!newModifier.equals(oldModifier)) {
      int index = modifiers.indexOf(oldModifier);
      if (index >= 0) {
        final StringBuilder buf = new StringBuilder(modifiers);
        buf.replace(index,
                    index + oldModifier.length() + ("".equals(newModifierStr) ? 1 : 0),
                    newModifierStr);
        modifiers = buf.toString();
      } else {
        if (!StringUtil.isEmpty(newModifierStr)) {
          newModifierStr += " ";
        }
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
      final CanonicalTypes.Type type = getReturnType();
      if (type != null) {
        buffer.append(type.getTypeText());
      }
      buffer.append(" ");
    }
    buffer.append(getMethodName());
    buffer.append("(");
    int paramIndent = buffer.toString().length();

    char[] chars = new char[paramIndent];
    Arrays.fill(chars, ' ');

    String indent = new String(chars);
    List<ParameterTableModelItemBase<ParameterInfoImpl>> items = myParametersTableModel.getItems();
    int curIndent = indent.length();
    for (int i = 0; i < items.size(); i++) {
      final ParameterTableModelItemBase<ParameterInfoImpl> item = items.get(i);
      if (i > 0) {
        buffer.append(",");
        buffer.append("\n");
        buffer.append(indent);
      }
      final String text = item.typeCodeFragment.getText();
      buffer.append(text).append(" ");
      final String name = item.parameter.getName();
      buffer.append(name);
      curIndent = indent.length() + text.length() + 1 + name.length();
    }
    //if (!items.isEmpty()) {
    //  buffer.append("\n");
    //}
    buffer.append(")");
    PsiTypeCodeFragment[] thrownExceptionsFragments = myExceptionsModel.getTypeCodeFragments();
    if (thrownExceptionsFragments.length > 0) {
      //buffer.append("\n");
      buffer.append(" throws ");
      curIndent += 9; // ") throws ".length()
      chars = new char[curIndent];
      Arrays.fill(chars, ' ');
      indent = new String(chars);
      for (int i = 0; i < thrownExceptionsFragments.length; i++) {
        String text = thrownExceptionsFragments[i].getText();
        if (i != 0) buffer.append(indent);
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
