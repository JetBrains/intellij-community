/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.generation.PsiElementClassMember;
import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.AssignFieldFromParameterAction;
import com.intellij.codeInsight.intention.impl.FieldFromParameterUtils;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.Function;
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
    if (myClass != null) {
      getFieldsToFix().add(myField);
    }
  }

  @Override
  @NotNull
  public String getText() {
    if (getFieldsToFix().size() > 1 && myClass.getConstructors().length <= 1) return "Add constructor parameters";
    return QuickFixBundle.message("add.constructor.parameter.name");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return isAvailable(getField());
  }

  private static boolean isAvailable(PsiField field) {
    PsiClass containingClass = field == null ? null : field.getContainingClass();
    return field != null
           && field.getManager().isInProject(field)
           && !field.hasModifierProperty(PsiModifier.STATIC)
           && containingClass != null
           && !(containingClass instanceof PsiSyntheticClass)
           && containingClass.getName() != null;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

    PsiMethod[] constructors = myClass.getConstructors();
    if (constructors.length == 0) {
      final AddDefaultConstructorFix defaultConstructorFix = new AddDefaultConstructorFix(myClass);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
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
    final ArrayList<PsiMethod> constrs = filterConstructorsIfFieldAlreadyAssigned(constructors, getField());
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
      try {
        final PsiMethod constructor = constrs.get(0);
        final LinkedHashSet<PsiField> fields = new LinkedHashSet<PsiField>();
        getFieldsToFix().add(myField);
        for (SmartPsiElementPointer<PsiField> elementPointer : fieldsToFix) {
          final PsiField field = elementPointer.getElement();
          if (field != null && isAvailable(field) && filterConstructorsIfFieldAlreadyAssigned(new PsiMethod[]{constructor}, field).contains(constructor)) {
            fields.add(field);
          }
        }
        if (constrs.size() == constructors.length && fields.size() > 1 && !ApplicationManager.getApplication().isUnitTestMode()) {
          PsiFieldMember[] members = new PsiFieldMember[fields.size()];
          int i = 0;
          for (PsiField field : fields) {
            members[i++] = new PsiFieldMember(field);
          }
          MemberChooser<PsiElementClassMember> chooser = new MemberChooser<PsiElementClassMember>(members, false, true, project);
          chooser.setTitle("Choose Fields to Generate Constructor Parameters for");
          chooser.show();
          if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE) return;
          final List<PsiElementClassMember> selectedElements = chooser.getSelectedElements();
          if (selectedElements == null) return;
          fields.clear();
          for (PsiElementClassMember member : selectedElements) {
            fields.add((PsiField)member.getElement());
          }
        }

        addParameterToConstructor(project, file, editor, constructor, constrs.size() == constructors.length
                                                                      ? fields.toArray(new PsiField[fields.size()])
                                                                      : new PsiField[]{getField()});
      }
      finally {
        fieldsToFix.clear();
      }
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
        PsiField field = psiVariable.getElement();
        if (field == null || !isAvailable(field)) return false;
        return finalFields.put(psiVariable, Boolean.TRUE) == null;
      }

      @NotNull
      @Override
      public Iterator<SmartPsiElementPointer<PsiField>> iterator() {
        return finalFields.keySet().iterator();
      }

      @Override
      public int size() {
        return finalFields.size();
      }

      @Override
      public void clear() {
        finalFields.clear();
      }
    };
  }

  private static ArrayList<PsiMethod> filterConstructorsIfFieldAlreadyAssigned(PsiMethod[] constructors, PsiField field) {
    final ArrayList<PsiMethod> result = new ArrayList<PsiMethod>(Arrays.asList(constructors));
    for (PsiReference reference : ReferencesSearch.search(field, new LocalSearchScope(constructors))) {
      final PsiElement element = reference.getElement();
      if (element instanceof PsiReferenceExpression && PsiUtil.isOnAssignmentLeftHand((PsiExpression)element)) {
        result.remove(PsiTreeUtil.getParentOfType(element, PsiMethod.class));
      }
    }
    return result;
  }

  private static boolean addParameterToConstructor(final Project project,
                                                   final PsiFile file,
                                                   final Editor editor,
                                                   final PsiMethod constructor,
                                                   final PsiField[] fields) throws IncorrectOperationException {
    final PsiParameterList parameterList = constructor.getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();
    ParameterInfoImpl[] newParamInfos = new ParameterInfoImpl[parameters.length + fields.length];
    final List<PsiVariable> params = new ArrayList<PsiVariable>(Arrays.asList(parameters));
    Collections.addAll(params, fields);
    Collections.sort(params, new FieldParameterComparator(parameterList));

    int i = 0;
    final HashMap<PsiField, String> usedFields = new HashMap<PsiField, String>();
    for (PsiVariable param : params) {
      final PsiType paramType = param.getType();
      if (param instanceof PsiParameter) {
        newParamInfos[i++] = new ParameterInfoImpl(parameterList.getParameterIndex((PsiParameter)param), param.getName(), paramType, param.getName());
      } else {
        final String uniqueParameterName = getUniqueParameterName(parameters, param, usedFields);
        usedFields.put((PsiField)param, uniqueParameterName);
        newParamInfos[i++] = new ParameterInfoImpl(-1, uniqueParameterName, paramType, uniqueParameterName);
      }
    }
    final SmartPointerManager manager = SmartPointerManager.getInstance(project);
    final SmartPsiElementPointer constructorPointer = manager.createSmartPsiElementPointer(constructor);

    final PsiMethod fromText = JavaPsiFacade.getElementFactory(project).createMethodFromText(createDummyMethod(constructor, newParamInfos),
                                                                                             constructor);
    final PsiClass containingClass = constructor.getContainingClass();
    if (containingClass == null) return false;
    final int minUsagesNumber = containingClass.findMethodsBySignature(fromText, false).length > 0 ? 0 : 1;
    final List<ParameterInfoImpl> parameterInfos =
      ChangeMethodSignatureFromUsageFix.performChange(project, editor, file, constructor, minUsagesNumber, newParamInfos, true, true);

    final ParameterInfoImpl[] resultParams = parameterInfos != null ? parameterInfos.toArray(new ParameterInfoImpl[parameterInfos.size()]) :
                                             newParamInfos;
    return ApplicationManager.getApplication().runWriteAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return doCreate(project, editor, parameters, constructorPointer, resultParams, usedFields);
      }
    });
  }

  private static String createDummyMethod(PsiMethod constructor, ParameterInfoImpl[] newParamInfos) {
    return constructor.getName() + "(" + StringUtil.join(newParamInfos, new Function<ParameterInfoImpl, String>() {
      @Override
      public String fun(ParameterInfoImpl info) {
        return info.getTypeText() + " " + info.getName();
      }
    }, ", ") + "){}";
  }

  private static String getUniqueParameterName(PsiParameter[] parameters, PsiVariable variable, HashMap<PsiField, String> usedNames) {
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(variable.getProject());
    final SuggestedNameInfo nameInfo = styleManager
      .suggestVariableName(VariableKind.PARAMETER,
                           styleManager.variableNameToPropertyName(variable.getName(), VariableKind.FIELD),
                           null, variable.getType());
    String newName = nameInfo.names[0];
    int n = 1;
    while (true) {
      if (isUnique(parameters, newName, usedNames)) {
        break;
      }
      newName = nameInfo.names[0] + n++;
    }
    return newName;
  }

  private static boolean isUnique(PsiParameter[] params, String newName, HashMap<PsiField, String> usedNames) {
    if (usedNames.containsValue(newName)) return false;
    for (PsiParameter parameter : params) {
      if (Comparing.strEqual(parameter.getName(), newName)) {
        return false;
      }
    }
    return true;
  }

  private static boolean doCreate(Project project, Editor editor, PsiParameter[] parameters, SmartPsiElementPointer constructorPointer,
                                  ParameterInfoImpl[] parameterInfos, HashMap<PsiField, String> fields) {
    PsiMethod constructor = (PsiMethod)constructorPointer.getElement();
    assert constructor != null;
    PsiParameter[] newParameters = constructor.getParameterList().getParameters();
    if (newParameters == parameters) return false; //user must have canceled dialog
    boolean created = false;
    // do not introduce assignment in chanined constructor
    if (JavaHighlightUtil.getChainedConstructors(constructor) == null) {
      for (PsiField field : fields.keySet()) {
        final String defaultParamName = fields.get(field);
        PsiParameter parameter = findParamByName(defaultParamName, field.getType(), newParameters, parameterInfos);
        if (parameter == null) {
          continue;
        }
        notNull(project, field, parameter);
        AssignFieldFromParameterAction.addFieldAssignmentStatement(project, field, parameter, editor);
        created = true;
      }
    }
    return created;
  }

  private static void notNull(Project project, PsiField field, PsiParameter parameter) {
    final String notNull = NullableNotNullManager.getInstance(field.getProject()).getNotNull(field);
    if (notNull != null) {
      final PsiAnnotation annotation = JavaPsiFacade.getElementFactory(project).createAnnotationFromText("@" + notNull, field);
      parameter.getModifierList().addBefore(annotation, null);
    }
  }

  @Nullable
  private static PsiParameter findParamByName(String newName,
                                              PsiType type,
                                              PsiParameter[] newParameters,
                                              ParameterInfoImpl[] parameterInfos) {
    for (PsiParameter newParameter : newParameters) {
      if (Comparing.strEqual(newName, newParameter.getName())) {
        return newParameter;
      }
    }
    for (int i = 0; i < newParameters.length; i++) {
      if (parameterInfos[i].getOldIndex() == -1) {
        final PsiParameter parameter = newParameters[i];
        final PsiType paramType = parameterInfos[i].getTypeWrapper().getType(parameter, parameter.getManager());
        if (type.isAssignableFrom(paramType)){
          return parameter;
        }
      }
    }
    return null;
  }

  private PsiField getField() {
    return myField.getElement();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private static class FieldParameterComparator implements Comparator<PsiVariable> {
    private final PsiParameterList myParameterList;

    public FieldParameterComparator(PsiParameterList parameterList) {
      myParameterList = parameterList;
    }

    @Override
    public int compare(PsiVariable o1, PsiVariable o2) {

      if (o1 instanceof PsiParameter && ((PsiParameter)o1).isVarArgs()) return 1;
      if (o2 instanceof PsiParameter && ((PsiParameter)o2).isVarArgs()) return -1;

      if (o1 instanceof PsiField && o2 instanceof PsiField) {
        return o1.getTextOffset() - o2.getTextOffset();
      }
      if (o1 instanceof PsiParameter && o2 instanceof PsiParameter) {
        return myParameterList.getParameterIndex((PsiParameter)o1) - myParameterList.getParameterIndex((PsiParameter)o2);
      }

      if (o1 instanceof PsiField && o2 instanceof PsiParameter) {
        final PsiField field = FieldFromParameterUtils.getParameterAssignedToField((PsiParameter)o2);
        if (field == null) return 1;
        return o1.getTextOffset() - field.getTextOffset();
      }
      if (o1 instanceof PsiParameter && o2 instanceof PsiField) {
        final PsiField field = FieldFromParameterUtils.getParameterAssignedToField((PsiParameter)o1);
        if (field == null) return -1;
        return field.getTextOffset() - o2.getTextOffset();
      }

      return 0;
    }
  }
}
