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
package com.intellij.refactoring.typeMigration.ui;

import com.intellij.find.FindSettings;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.TypeMigrationRules;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.ui.EditorComboBox;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

/**
 * @author anna
 * Date: 25-Mar-2008
 */
public class TypeMigrationDialog extends RefactoringDialog {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.typeMigration.ui.TypeMigrationDialog");

  public static final String REFACTORING_NAME = "Type Migration";

  private final EditorComboBox myToTypeEditor;
  private final PsiElement myRoot;
  private TypeMigrationRules myRules;
  private final PsiTypeCodeFragment myTypeCodeFragment;
  private final ScopeChooserCombo myScopeChooserCombo;

  public TypeMigrationDialog(@NotNull Project project, PsiElement root, TypeMigrationRules rules) {
    super(project, false);
    myRoot = root;
    myRules = rules;

    final PsiType migrationRootType = rules != null ? rules.getMigrationRootType() : null;
    final PsiType rootType = getRootType();
    final String text = migrationRootType != null ? migrationRootType.getPresentableText()
                                                  : rootType != null ? rootType.getPresentableText() : "";
    int flags = 0;
    if (root instanceof PsiParameter) {
      final PsiElement scope = ((PsiParameter)root).getDeclarationScope();
      if (scope instanceof PsiMethod) {
        flags |= JavaCodeFragmentFactory.ALLOW_ELLIPSIS;
      }
      else if (scope instanceof PsiCatchSection && PsiUtil.getLanguageLevel(root).isAtLeast(LanguageLevel.JDK_1_7)) {
        flags |= JavaCodeFragmentFactory.ALLOW_DISJUNCTION;
      }
    }
    myTypeCodeFragment = JavaCodeFragmentFactory.getInstance(project).createTypeCodeFragment(text, root, true, flags);

    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final Document document = documentManager.getDocument(myTypeCodeFragment);
    assert document != null;
    myToTypeEditor = new EditorComboBox(document, project, StdFileTypes.JAVA);
    final String[] types = getValidTypes(project, root);
    if (types != null) {
      myToTypeEditor.setHistory(types);
    } else {
      myToTypeEditor.setHistory(new String[]{document.getText()});
    }
    document.addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(final DocumentEvent e) {
        documentManager.commitDocument(document);
        validateButtons();
      }
    });

    myScopeChooserCombo = new ScopeChooserCombo(project, false, true, FindSettings.getInstance().getDefaultScopeName());
    Disposer.register(myDisposable, myScopeChooserCombo);
    myScopeChooserCombo.getChildComponent().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        validateButtons();
      }
    });
    init();
    setTitle(REFACTORING_NAME);
  }

  public PsiElement getRoot() {
    return myRoot;
  }

  @Nullable
  public PsiType getMigrationType() {
    try {
      return myTypeCodeFragment.getType();
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException e) {
      LOG.info(e);
      return null;
    }
    catch (PsiTypeCodeFragment.NoTypeException e) {
      LOG.info(e);
      return null;
    }
  }

  @Nullable
  private String[] getValidTypes(final Project project, final PsiElement root) {
    final ArrayList<PsiExpression> expressions = new ArrayList<PsiExpression>();
    if (root instanceof PsiField || root instanceof PsiMethod) {
      final PsiModifierList modifierList = ((PsiModifierListOwner)root).getModifierList();
      if (VisibilityUtil.compare(VisibilityUtil.getVisibilityModifier(modifierList), PsiModifier.PRIVATE) < 0) return null;
    }

    for (PsiReference reference : ReferencesSearch.search(root, GlobalSearchScope.fileScope(root.getContainingFile()))) {
      final PsiElement element = reference.getElement();
      final PsiExpression expr = PsiTreeUtil.getParentOfType(element, PsiExpression.class, false);
      if (expr != null) {
        expressions.add(expr);
      }
    }
    try {
      final PsiExpression[] occurrences = expressions.toArray(new PsiExpression[expressions.size()]);
      final PsiType[] psiTypes = new TypeSelectorManagerImpl(project, myTypeCodeFragment.getType(), occurrences).getTypesForAll();
      if (psiTypes.length > 0) {
        final String[] history = new String[psiTypes.length];
        for (int i = 0; i < psiTypes.length; i++) {
          PsiType psiType = psiTypes[i];
          history[i] = psiType.getCanonicalText();
        }
        return history;
      }
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException e) {
      LOG.info(e);
      return null;
    }
    catch (PsiTypeCodeFragment.NoTypeException e) {
      LOG.info(e);
      return null;
    }
    return null;
  }

  @Override
  protected void canRun() throws ConfigurationException {
    if (!checkType(getMigrationType())) throw new ConfigurationException("\'" + myTypeCodeFragment.getText() + "\' is invalid type");
    if (myScopeChooserCombo.getSelectedScope() == null) throw new ConfigurationException("Scope is not chosen");
  }

  private static boolean checkType(final PsiType type) {
    if (type == null) return false;
    if (!type.isValid()) return false;
    if (type instanceof PsiClassType){
      final PsiClassType psiClassType = (PsiClassType)type;
      if (psiClassType.resolve() == null) return false;
      final PsiType[] types = psiClassType.getParameters();
      for (PsiType paramType : types) {
        if (paramType instanceof PsiPrimitiveType || (paramType instanceof PsiWildcardType && ((PsiWildcardType)paramType).getBound() instanceof PsiPrimitiveType)) return false;
        if (!checkType(paramType)) return false;
      }
    }
    if (type instanceof PsiArrayType) {
      return checkType(type.getDeepComponentType());
    }
    return true;
  }

  protected void doAction() {
    FindSettings.getInstance().setDefaultScopeName(myScopeChooserCombo.getSelectedScopeName());

    final PsiType rootType = getRootType();
    final CanonicalTypes.Type typeWrapper = CanonicalTypes.createTypeWrapper(getMigrationType());
    assert typeWrapper != null : getMigrationType();
    final PsiType migrationType = typeWrapper.getType(myRoot, myRoot.getManager());

    if (Comparing.equal(rootType, migrationType)) {
      close(DialogWrapper.OK_EXIT_CODE);
      return;
    }

    if (myRules == null) {
      myRules = new TypeMigrationRules(rootType);
      myRules.setMigrationRootType(migrationType);
      myRules.setBoundScope(myScopeChooserCombo.getSelectedScope());
    }
    invokeRefactoring(new TypeMigrationProcessor(myProject, myRoot, myRules));
  }

  @Nullable
  private PsiType getRootType() {
    return TypeMigrationLabeler.getElementType(myRoot);
  }

  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.NORTHWEST,
                                                         GridBagConstraints.HORIZONTAL, new Insets(5, 5, 0, 0), 0, 0);
    final PsiType type = getRootType();
    panel.add(new JLabel("Migrate " + getElementPresentation(myRoot) + " \"" + (type != null ? type.getCanonicalText() : "<unknown>") + "\" to"), gc);
    panel.add(myToTypeEditor, gc);

    LabeledComponent<ScopeChooserCombo> scopeChooserComponent = new LabeledComponent<ScopeChooserCombo>();
    scopeChooserComponent.setComponent(myScopeChooserCombo);
    scopeChooserComponent.setText("Choose scope where change signature may occur");
    panel.add(scopeChooserComponent, gc);
    return panel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myToTypeEditor;
  }

  private static String getElementPresentation(PsiElement element) {
    if (element instanceof PsiMethod) {
      return "return type of method " + ((PsiMethod)element).getName();
    }

    if (element instanceof PsiField) {
      return "type of field " + ((PsiField)element).getName();
    }

    if (element instanceof PsiLocalVariable) {
      return "type of variable " + ((PsiLocalVariable)element).getName();
    }

    if (element instanceof PsiParameter) {
      final PsiParameter param = (PsiParameter)element;
      String result = "type of parameter " + param.getName();
      if (param.getParent() instanceof PsiParameterList) {
        final PsiMethod method = PsiTreeUtil.getParentOfType(param, PsiMethod.class);
        assert method != null;
        result  += " of method " + method.getName();
      }
      return result;
    }

    return element.toString();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("reference.typeMigrationDialog");
  }
}
