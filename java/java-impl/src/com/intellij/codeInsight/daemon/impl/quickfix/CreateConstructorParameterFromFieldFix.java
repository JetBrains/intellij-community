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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.AssignFieldFromParameterAction;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CreateConstructorParameterFromFieldFix implements IntentionAction {
  private static final Key<Map<SmartPsiElementPointer<PsiField>, Boolean>> FIELDS = Key.create("CONSTRUCTOR_PARAMS");

  private final SmartPsiElementPointer<PsiField> myField;
  private final PsiClass myClass;

  public CreateConstructorParameterFromFieldFix(@NotNull PsiField field) {
    myClass = field.getContainingClass();
    myField = SmartPointerManager.getInstance(field.getProject()).createSmartPsiElementPointer(field);
    getFieldsToFix().add(myField);
  }

  @NotNull
  public String getText() {
    if (getFieldsToFix().size() > 1 && myClass.getConstructors().length <= 1) return "Add constructor parameters";
    return QuickFixBundle.message("add.constructor.parameter.name");
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiField field = getField();
    PsiClass containingClass = field == null ? null : field.getContainingClass();
    return
           field != null
           && field.getManager().isInProject(field)
           && !field.hasModifierProperty(PsiModifier.STATIC)
           && containingClass != null
           && !(containingClass instanceof JspClass)
           && containingClass.getName() != null
      ;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;

    PsiMethod[] constructors = myClass.getConstructors();
    if (constructors.length == 0) {
      final AddDefaultConstructorFix defaultConstructorFix = new AddDefaultConstructorFix(myClass);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          defaultConstructorFix.invoke(project, editor, file);
        }
      });
      constructors = myClass.getConstructors();
    }
    Arrays.sort(constructors, new Comparator<PsiMethod>() {
      @Override
      public int compare(PsiMethod c1, PsiMethod c2) {
        final PsiMethod cc1 = RefactoringUtil.getChainedConstructor(c1);
        final PsiMethod cc2 = RefactoringUtil.getChainedConstructor(c2);
        if (cc1 == c2) return 1;
        if (cc2 == c1) return -1;
        if (cc1 == null) {
          return cc2 == null ? 0 : compare(c1, cc2);
        } else {
          return cc2 == null ? compare(cc1, c2) : compare(cc1, cc2);
        }
      }
    });
    final ArrayList<PsiMethod> constrs = filterConstructorsIfFieldAlreadyAssigned(constructors);
    if (constrs.size() > 1) {
      final PsiMethodMember[] members = new PsiMethodMember[constrs.size()];
      int i = 0;
      for (PsiMethod constructor : constrs) {
        members[i++] = new PsiMethodMember(constructor);
      }
      final List<PsiMethodMember> elements;
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        elements = Arrays.asList(members);
      } else {
        final MemberChooser<PsiMethodMember> chooser = new MemberChooser<PsiMethodMember>(members, false, true, project);
        chooser.setTitle("Choose constructors to add parameter to");
        chooser.show();
        elements = chooser.getSelectedElements();
        if (elements == null) return;
      }

      for (PsiMethodMember member : elements) {
        if (!addParameterToConstructor(project, file, editor, member.getElement(), new PsiField[] {getField()})) break;
      }

    } else if (!constrs.isEmpty()) {
      final Collection<SmartPsiElementPointer<PsiField>> fieldsToFix = getFieldsToFix();
      final List<PsiField> fields = new ArrayList<PsiField>();
      for (SmartPsiElementPointer<PsiField> elementPointer : fieldsToFix) {
        final PsiField field = elementPointer.getElement();
        if (field != null) {
          fields.add(field);
        }
      }
      addParameterToConstructor(project, file, editor, constrs.get(0), constrs.size() == constructors.length ? fields.toArray(new PsiField[fields.size()]) : new PsiField[]{getField()});
    }
  }

   @NotNull
  private Collection<SmartPsiElementPointer<PsiField>> getFieldsToFix() {
    Map<SmartPsiElementPointer<PsiField>, Boolean> fields = myClass.getUserData(FIELDS);
    if (fields == null) myClass.putUserData(FIELDS, fields = new ConcurrentWeakHashMap<SmartPsiElementPointer<PsiField>,Boolean>(1));
    final Map<SmartPsiElementPointer<PsiField>, Boolean> finalFields = fields;
    return new AbstractCollection<SmartPsiElementPointer<PsiField>>() {
      @Override
      public boolean add(SmartPsiElementPointer<PsiField> psiVariable) {
        return finalFields.put(psiVariable, Boolean.TRUE) == null;
      }

      @Override
      public Iterator<SmartPsiElementPointer<PsiField>> iterator() {
        return finalFields.keySet().iterator();
      }

      @Override
      public int size() {
        return finalFields.size();
      }
    };
  }

  private ArrayList<PsiMethod> filterConstructorsIfFieldAlreadyAssigned(PsiMethod[] constructors) {
    final ArrayList<PsiMethod> result = new ArrayList<PsiMethod>(Arrays.asList(constructors));
    for (PsiReference reference : ReferencesSearch.search(getField(), new LocalSearchScope(constructors))) {
      final PsiElement element = reference.getElement();
      if (element instanceof PsiReferenceExpression && PsiUtil.isOnAssignmentLeftHand((PsiExpression)element)) {
        final PsiExpression rExpression = ((PsiAssignmentExpression)element.getParent()).getRExpression();
        if (rExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression)rExpression).resolve() instanceof PsiParameter) {
          result.remove(PsiTreeUtil.getParentOfType(element, PsiMethod.class));
        }
      }
    }
    return result;
  }

  private static boolean addParameterToConstructor(final Project project,
                                                   final PsiFile file,
                                                   final Editor editor,
                                                   final PsiMethod constructor,
                                                   final PsiField[] fields) throws IncorrectOperationException {
    final PsiParameter[] parameters = constructor.getParameterList().getParameters();
    PsiExpression[] expressions = new PsiExpression[parameters.length+fields.length];
    PsiElementFactory factory = JavaPsiFacade.getInstance(file.getProject()).getElementFactory();
    int i = 0;
    for (; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      String value = PsiTypesUtil.getDefaultValueOfType(parameter.getType());
      expressions[i] = factory.createExpressionFromText(value, parameter);
    }
    for (PsiField field : fields) {
      expressions[i++] = factory.createExpressionFromText(field.getName(), constructor);
    }
    if (constructor.isVarArgs()) {
      ArrayUtil.swap(expressions, parameters.length - 1, expressions.length - 1);
    }
    final SmartPointerManager manager = SmartPointerManager.getInstance(project);
    final SmartPsiElementPointer constructorPointer = manager.createSmartPsiElementPointer(constructor);

    final ChangeMethodSignatureFromUsageFix addParamFix = new ChangeMethodSignatureFromUsageFix(constructor, expressions, PsiSubstitutor.EMPTY, constructor, true, 1);
    if (addParamFix.isAvailable(project, editor, file)) {
      addParamFix.invoke(project, editor, file);
    } else if (addParamFix.isMethodSignatureExists() && !ApplicationManager.getApplication().isUnitTestMode()) {
      HintManager.getInstance().showErrorHint(editor, "Constructor with corresponding signature already exist");
    }
    return ApplicationManager.getApplication().runWriteAction(new Computable<Boolean>() {
      public Boolean compute() {
        return doCreate(project, editor, parameters, constructorPointer, addParamFix, fields);
      }
    });
  }

  private static boolean doCreate(Project project, Editor editor, PsiParameter[] parameters, SmartPsiElementPointer constructorPointer,
                                  ChangeMethodSignatureFromUsageFix addParamFix, PsiField[] fields) {
    PsiMethod constructor = (PsiMethod)constructorPointer.getElement();
    assert constructor != null;
    PsiParameter[] newParameters = constructor.getParameterList().getParameters();
    if (newParameters == parameters) return false; //user must have canceled dialog
    boolean created = false;
    // do not introduce assignment in chanined constructor
    if (HighlightControlFlowUtil.getChainedConstructors(constructor) == null) {
      final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);

      for (PsiField field : fields) {
        final String defaultParamName = styleManager
          .suggestVariableName(VariableKind.PARAMETER, styleManager.variableNameToPropertyName(field.getName(), VariableKind.FIELD), null,
                               field.getType()).names[0];
        PsiParameter parameter = findParamByName(defaultParamName, newParameters);
        if (parameter == null) {
          parameter = fields.length == 1 ? findParamByName(addParamFix.getNewParameterNameByOldIndex(-1), newParameters) : null;
          if (parameter == null) {
            continue;
          }
        }
        AssignFieldFromParameterAction.addFieldAssignmentStatement(project, field, parameter, editor);
        created = true;
      }
    }
    return created;
  }

  @Nullable
  private static PsiParameter findParamByName(String newName, PsiParameter[] newParameters) {
    PsiParameter parameter = null;
    for (PsiParameter newParameter : newParameters) {
      if (Comparing.strEqual(newName, newParameter.getName())) {
        parameter = newParameter;
        break;
      }
    }
    return parameter;
  }

  private PsiField getField() {
    return myField.getElement();
  }

  public boolean startInWriteAction() {
    return false;
  }
}
