/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
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
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.table.TableView;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.*;
import com.intellij.util.ui.DialogUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.table.EditorTextFieldJBTableRowRenderer;
import com.intellij.util.ui.table.JBTableRow;
import com.intellij.util.ui.table.JBTableRowEditor;
import com.intellij.util.ui.table.JBTableRowRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.intellij.refactoring.changeSignature.ChangeSignatureHandler.REFACTORING_NAME;

/**
 * @author Konstantin Bulenkov
 */
public class JavaChangeSignatureDialog extends ChangeSignatureDialogBase<ParameterInfoImpl, PsiMethod, String, JavaMethodDescriptor, ParameterTableModelItemBase<ParameterInfoImpl>, JavaParameterTableModel> {
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

  public static JavaChangeSignatureDialog createAndPreselectNew(final Project project,
                                                                final PsiMethod method,
                                                                final List<ParameterInfoImpl> parameterInfos,
                                                                final boolean allowDelegation, final PsiReferenceExpression refExpr) {
    return new JavaChangeSignatureDialog(project, method, allowDelegation, refExpr) {
      @Override
      protected int getSelectedIdx() {
        for (int i = 0; i < parameterInfos.size(); i++) {
          ParameterInfoImpl info = parameterInfos.get(i);
          if (info.oldParameterIndex < 0) {
            return i;
          }
        }
        return super.getSelectedIdx();
      }
    };
  }

  @Override
  protected VisibilityPanelBase<String> createVisibilityControl() {
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
    table.setStriped(true);
    table.setRowHeight(20);
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

    myPropExceptionsButton = new AnActionButton(RefactoringBundle.message("changeSignature.propagate.exceptions.title"), null, AllIcons.Hierarchy.Caller) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final Ref<JavaCallerChooser> chooser = new Ref<>();
        Consumer<Set<PsiMethod>> callback = psiMethods -> {
          myMethodsToPropagateExceptions = psiMethods;
          myExceptionPropagationTree = chooser.get().getTree();
        };
        chooser.set(new JavaCallerChooser(myMethod.getMethod(),
                                          myProject,
                                          RefactoringBundle.message("changeSignature.exception.caller.chooser"),
                                          myExceptionPropagationTree,
                                          callback));
        chooser.get().show();
      }
    };
    myPropExceptionsButton.setShortcut(CustomShortcutSet.fromString("alt X"));

    final JPanel panel = ToolbarDecorator.createDecorator(table).addExtraAction(myPropExceptionsButton).createPanel();
    panel.setBorder(IdeBorderFactory.createEmptyBorder());

    myExceptionsModel.addTableModelListener(mySignatureUpdater);

    final ArrayList<Pair<String, JPanel>> result = new ArrayList<>();
    final String message = RefactoringBundle.message("changeSignature.exceptions.panel.border.title");
    result.add(Pair.create(message, panel));
    return result;
  }

  @Override
  protected LanguageFileType getFileType() {
    return StdFileTypes.JAVA;
  }

  @Override
  protected JavaParameterTableModel createParametersInfoModel(JavaMethodDescriptor descriptor) {
    final PsiParameterList parameterList = descriptor.getMethod().getParameterList();
    return new JavaParameterTableModel(parameterList, myDefaultValueContext, this);
  }

  @Override
  protected boolean isListTableViewSupported() {
    return true;
  }

  @Override
  protected ParametersListTable createParametersListTable() {
    return new ParametersListTable() {
      private final EditorTextFieldJBTableRowRenderer myRowRenderer =
        new EditorTextFieldJBTableRowRenderer(getProject(), JavaChangeSignatureDialog.this.getFileType(), myDisposable) {
        @Override
        protected String getText(JTable table, int row) {
          ParameterTableModelItemBase<ParameterInfoImpl> item = getRowItem(row);
          final String typeText = item.typeCodeFragment.getText();
          final String separator = StringUtil.repeatSymbol(' ', getTypesMaxLength() - typeText.length() + 1);
          String text = typeText + separator + item.parameter.getName();
          final String defaultValue = item.defaultValueCodeFragment.getText();
          String tail = "";
          if (StringUtil.isNotEmpty(defaultValue)) {
            tail += " default value = " + defaultValue;
          }
          if (item.parameter.isUseAnySingleVariable()) {
            if (StringUtil.isNotEmpty(defaultValue)) {
              tail += ";";
            }
            tail += " Use any var.";
          }
          if (!StringUtil.isEmpty(tail)) {
            text += " //" + tail;
          }
          return " " + text;
        }
      };

      @Override
      protected JBTableRowRenderer getRowRenderer(int row) {
        return myRowRenderer;
      }

      @NotNull
      @Override
      protected JBTableRowEditor getRowEditor(final ParameterTableModelItemBase<ParameterInfoImpl> item) {
        return new JBTableRowEditor() {
          private EditorTextField myTypeEditor;
          private EditorTextField myNameEditor;
          private EditorTextField myDefaultValueEditor;
          private JCheckBox myAnyVar;

          @Override
          public void prepareEditor(JTable table, int row) {
            setLayout(new BorderLayout());
            final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(item.typeCodeFragment);
            myTypeEditor = new EditorTextField(document, getProject(), getFileType());
            myTypeEditor.addDocumentListener(mySignatureUpdater);
            myTypeEditor.setPreferredWidth(getTable().getWidth() / 2);
            myTypeEditor.addDocumentListener(new RowEditorChangeListener(0));
            add(createLabeledPanel("Type:", myTypeEditor), BorderLayout.WEST);

            myNameEditor = new EditorTextField(item.parameter.getName(), getProject(), getFileType());
            myNameEditor.addDocumentListener(mySignatureUpdater);
            myNameEditor.addDocumentListener(new RowEditorChangeListener(1));
            add(createLabeledPanel("Name:", myNameEditor), BorderLayout.CENTER);
            new TextFieldCompletionProvider() {

              @Override
              protected void addCompletionVariants(@NotNull String text,
                                                   int offset,
                                                   @NotNull String prefix,
                                                   @NotNull CompletionResultSet result) {
                final PsiCodeFragment fragment = item.typeCodeFragment;
                if (fragment instanceof PsiTypeCodeFragment) {
                  final PsiType type;
                  try {
                    type = ((PsiTypeCodeFragment)fragment).getType();
                  }
                  catch (Exception e) {
                    return;
                  }
                  final SuggestedNameInfo info = JavaCodeStyleManager.getInstance(myProject)
                    .suggestVariableName(VariableKind.PARAMETER, null, null, type);

                  for (String completionVariant : info.names) {
                    final LookupElementBuilder element = LookupElementBuilder.create(completionVariant);
                    result.addElement(element.withLookupString(completionVariant.toLowerCase(Locale.ENGLISH)));
                  }
                }
              }
            }.apply(myNameEditor, item.parameter.getName());

            if (!item.isEllipsisType() && item.parameter.getOldIndex() == -1) {
              final JPanel additionalPanel = new JPanel(new BorderLayout());
              final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(item.defaultValueCodeFragment);
              myDefaultValueEditor = new EditorTextField(doc, getProject(), getFileType());
              ((PsiExpressionCodeFragment)item.defaultValueCodeFragment).setExpectedType(getRowType(item));
              myDefaultValueEditor.setPreferredWidth(getTable().getWidth() / 2);
              myDefaultValueEditor.addDocumentListener(new RowEditorChangeListener(2));
              additionalPanel.add(createLabeledPanel("Default value:", myDefaultValueEditor), BorderLayout.WEST);

              if (!isGenerateDelegate()) {
                myAnyVar = new JCheckBox("&Use Any Var");
                UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, myAnyVar);
                DialogUtil.registerMnemonic(myAnyVar, '&');
                myAnyVar.addActionListener(new ActionListener() {
                  @Override
                  public void actionPerformed(ActionEvent e) {
                    item.parameter.setUseAnySingleVariable(myAnyVar.isSelected());
                  }
                });
                final JPanel anyVarPanel = new JPanel(new BorderLayout());
                anyVarPanel.add(myAnyVar, BorderLayout.SOUTH);
                UIUtil.addInsets(anyVarPanel, JBUI.insetsBottom(8));
                additionalPanel.add(anyVarPanel, BorderLayout.CENTER);
                //additionalPanel.setPreferredSize(new Dimension(t.getWidth() / 3, -1));
              }
              add(additionalPanel, BorderLayout.SOUTH);
            }
          }

          @Override
          public JBTableRow getValue() {
            return new JBTableRow() {
              @Override
              public Object getValueAt(int column) {
                switch (column) {
                  case 0: return item.typeCodeFragment;
                  case 1: return myNameEditor.getText().trim();
                  case 2: return item.defaultValueCodeFragment;
                  case 3: return myAnyVar != null && myAnyVar.isSelected();
                }
                return null;
              }
            };
          }

          @Override
          public JComponent getPreferredFocusedComponent() {
            final MouseEvent me = getMouseEvent();
            if (me == null) {
              return myTypeEditor.getFocusTarget();
            }
            final double x = me.getPoint().getX();
            return x <= getTypesColumnWidth()
                   ? myTypeEditor.getFocusTarget()
                   : myDefaultValueEditor == null || x <= getNamesColumnWidth()
                     ? myNameEditor.getFocusTarget()
                     : myDefaultValueEditor.getFocusTarget();
          }

          @Override
          public JComponent[] getFocusableComponents() {
            final List<JComponent> focusable = new ArrayList<>();
            focusable.add(myTypeEditor.getFocusTarget());
            focusable.add(myNameEditor.getFocusTarget());
            if (myDefaultValueEditor != null) {
              focusable.add(myDefaultValueEditor.getFocusTarget());
            }
            if (myAnyVar != null) {
              focusable.add(myAnyVar);
            }
            return focusable.toArray(new JComponent[focusable.size()]);
          }
        };
      }

      @Override
      protected boolean isRowEmpty(int row) {
        ParameterInfoImpl parameter = getRowItem(row).parameter;
        return StringUtil.isEmpty(parameter.getName()) && StringUtil.isEmpty(parameter.getTypeText());
      }
    };
  }

  private int getTypesMaxLength() {
    int len = 0;
    for (ParameterTableModelItemBase<ParameterInfoImpl> item : myParametersTableModel.getItems()) {
      final String text = item.typeCodeFragment == null ? null : item.typeCodeFragment.getText();
      len = Math.max(len, text == null ? 0 : text.length());
    }
    return len;
  }
  
  private int getNamesMaxLength() {
    int len = 0;
    for (ParameterTableModelItemBase<ParameterInfoImpl> item : myParametersTableModel.getItems()) {
      final String text = item.parameter.getName();
      len = Math.max(len, text == null ? 0 : text.length());
    }
    return len;
  }  
  
  private int getColumnWidth(int index) {
    int letters = getTypesMaxLength() + (index == 0 ? 1 : getNamesMaxLength() + 2);
    Font font = EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN);
    font = new Font(font.getFontName(), font.getStyle(), 12);
    return  letters * Toolkit.getDefaultToolkit().getFontMetrics(font).stringWidth("W");
  }

  private int getTypesColumnWidth() {
    return  getColumnWidth(0);
  }    
  
  private int getNamesColumnWidth() {
    return getColumnWidth(1);
  }

  @Nullable
  private static PsiType getRowType(ParameterTableModelItemBase<ParameterInfoImpl> item) {
    try {
      return ((PsiTypeCodeFragment)item.typeCodeFragment).getType();
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException e) {
      return null;
    }
    catch (PsiTypeCodeFragment.NoTypeException e) {
      return null;
    }
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
          animator.startAndDoWhenDone(() -> t.editCellAt(t.getRowCount() - 1, 0));
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
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(myProject);
    return factory.createTypeCodeFragment(returnTypeText, myMethod.getMethod(), true, JavaCodeFragmentFactory.ALLOW_VOID);
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
    if (!PsiNameHelper.getInstance(manager.getProject()).isIdentifier(name)) {
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
      final ParameterTableModelItemBase<ParameterInfoImpl> item = parameterInfos.get(i);

      if (!PsiNameHelper.getInstance(manager.getProject()).isIdentifier(item.parameter.getName())) {
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
        return RefactoringBundle.message("changeSignature.no.type.for.parameter", "return", item.parameter.getName());
      }

      item.parameter.setType(type);

      if (type instanceof PsiEllipsisType && i != newParametersNumber - 1) {
        return RefactoringBundle.message("changeSignature.vararg.not.last");
      }

      if (item.parameter.oldParameterIndex < 0) {
        String def = WriteCommandAction.runWriteCommandAction(myProject, new Computable<String>() {
          @Override
          public String compute() {
            return JavaCodeStyleManager.getInstance(myProject).qualifyClassReferences(item.defaultValueCodeFragment).getText().trim();
          }
        });
        item.parameter.defaultValue = def;
        if (!(type instanceof PsiEllipsisType)) {
          try {
            if (!StringUtil.isEmpty(def)) {
              factory.createExpressionFromText(def, null);
            }
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
                                          RefactoringBundle.message("changeSignature.refactoring.name"), Messages.getWarningIcon()) != Messages.OK) {
            return EXIT_SILENTLY;
          }
        }
      }
      for (ParameterTableModelItemBase<ParameterInfoImpl> item : parameterInfos) {

        if (!RefactoringUtil.isResolvableType(((PsiTypeCodeFragment)item.typeCodeFragment).getType())) {
          if (Messages.showOkCancelDialog(myProject, RefactoringBundle
            .message("changeSignature.cannot.resolve.parameter.type", item.typeCodeFragment.getText(), item.parameter.getName()),
                                          RefactoringBundle.message("changeSignature.refactoring.name"), Messages.getWarningIcon()) !=
              Messages.OK) {
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
  protected ValidationInfo doValidate() {
    if (!getTableComponent().isEditing()) {
      for (final ParameterTableModelItemBase<ParameterInfoImpl> item : myParametersTableModel.getItems()) {
        if (item.parameter.oldParameterIndex < 0) {
          if (StringUtil.isEmpty(item.defaultValueCodeFragment.getText()))
            return new ValidationInfo("Default value is missing. Method calls will contain blanks instead of the new parameter value.");
        }
      }
    }
    return super.doValidate();
  }

  @Override
  protected boolean postponeValidation() {
    return false;
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
    final String newModifier = ObjectUtils.notNull(getVisibility(), PsiModifier.PACKAGE_LOCAL);
    String newModifierStr = VisibilityUtil.getVisibilityString(newModifier);
    if (!Comparing.equal(newModifier, oldModifier)) {
      int index = modifiers.indexOf(oldModifier);
      if (index >= 0) {
        final StringBuilder buf = new StringBuilder(modifiers);
        buf.replace(index,
                    index + oldModifier.length() + (StringUtil.isEmpty(newModifierStr) ? 1 : 0),
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

    final int lineBreakIdx = buffer.lastIndexOf("\n");
    String indent = StringUtil.repeatSymbol(' ', lineBreakIdx >= 0 ? buffer.length() - lineBreakIdx - 1 : buffer.length());
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
      indent = StringUtil.repeatSymbol(' ', curIndent);
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
