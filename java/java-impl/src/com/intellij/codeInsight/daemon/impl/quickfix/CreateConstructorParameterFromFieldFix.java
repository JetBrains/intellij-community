// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.generation.PsiElementClassMember;
import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.AssignFieldFromParameterAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.impl.FieldFromParameterUtils;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CreateConstructorParameterFromFieldFix implements IntentionAction {
  private static final Key<Map<SmartPsiElementPointer<PsiField>, Boolean>> FIELDS = Key.create("CONSTRUCTOR_PARAMS");

  private final SmartPsiElementPointer<PsiField> myField;
  private final PsiClass myClass;
  private int myConstructorsLength;

  public CreateConstructorParameterFromFieldFix(@NotNull PsiField field) {
    myClass = field.getContainingClass();
    myField = SmartPointerManager.getInstance(field.getProject()).createSmartPsiElementPointer(field);
    if (myClass != null) {
      getFieldsToFix().add(myField);
      myConstructorsLength = myClass.getConstructors().length;
    }
  }

  @Override
  @NotNull
  public String getText() {
    if (getFieldsToFix().size() > 1 && myConstructorsLength <= 1) return QuickFixBundle.message("add.constructor.parameters");
    return QuickFixBundle.message("add.constructor.parameter.name");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return (myClass == null || myClass.isValid()) && isAvailable(getField());
  }

  private static boolean isAvailable(PsiField field) {
    PsiClass containingClass = field == null ? null : field.getContainingClass();
    return field != null
           && BaseIntentionAction.canModify(field)
           && !field.hasModifierProperty(PsiModifier.STATIC)
           && containingClass != null
           && !(containingClass instanceof PsiSyntheticClass)
           && !containingClass.isRecord()
           && containingClass.getName() != null;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile copyFile) {
    PsiClass copyMyClass = PsiTreeUtil.findSameElementInCopy(myClass, copyFile);
    PsiField myFieldElement = myField.getElement();
    if (myFieldElement == null) {
      return IntentionPreviewInfo.EMPTY;
    }
    PsiField copyMyField = PsiTreeUtil.findSameElementInCopy(myFieldElement, copyFile);
    PsiMethod[] constructors = getNonSyntheticConstructors(copyMyClass);
    if (constructors.length == 0) {
      final AddDefaultConstructorFix defaultConstructorFix = new AddDefaultConstructorFix(copyMyClass);
      defaultConstructorFix.invoke(project, editor, copyFile);
      constructors = copyMyClass.getConstructors();
    }
    List<PsiMethod> filteredConstructors = getFilteredConstructors(constructors, copyMyField);
    if (filteredConstructors.isEmpty()) {
      return IntentionPreviewInfo.EMPTY;
    }
    PsiMethod currentConstructor = filteredConstructors.get(0);
    PsiParameterList parameterList = currentConstructor.getParameterList();
    final String uniqueParameterName = getUniqueParameterName(parameterList.getParameters(), copyMyField, Map.of());
    ParameterInfoImpl info = ParameterInfoImpl.createNew()
      .withName(uniqueParameterName)
      .withType(AnnotationTargetUtil.keepStrictlyTypeUseAnnotations(copyMyField.getModifierList(), copyMyField.getType()))
      .withDefaultValue(uniqueParameterName);
    int parametersCount = parameterList.getParametersCount();
    PsiParameter newParameter =
      JavaPsiFacade.getElementFactory(project).createParameterFromText(info.getTypeText() + " " + info.getName(), currentConstructor);
    PsiParameter afterInsert;
    if (parametersCount == 0) {
      afterInsert = (PsiParameter)parameterList.add(newParameter);
    }
    else {
      PsiParameter lastParameter = parameterList.getParameter(parametersCount - 1);
      if (lastParameter == null) {
        return IntentionPreviewInfo.EMPTY;
      }
      if (lastParameter.isVarArgs()) {
        afterInsert = (PsiParameter)parameterList.addBefore(newParameter, lastParameter);
      }
      else {
        afterInsert = (PsiParameter)parameterList.addAfter(newParameter, lastParameter);
      }
    }
    if (JavaHighlightUtil.getChainedConstructors(currentConstructor).isEmpty()) {
      AssignFieldFromParameterAction.addFieldAssignmentStatement(project, copyMyField, afterInsert, editor);
    }
    return IntentionPreviewInfo.DIFF;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

    PsiMethod[] constructors = getNonSyntheticConstructors(myClass);
    if (constructors.length == 0) {
      final AddDefaultConstructorFix defaultConstructorFix = new AddDefaultConstructorFix(myClass);
      ApplicationManager.getApplication().runWriteAction(() -> defaultConstructorFix.invoke(project, editor, file));
      constructors = getNonSyntheticConstructors(myClass);
    }
    final List<PsiMethod> filtered = getFilteredConstructors(constructors, getField());
    final List<SmartPsiElementPointer<PsiElement>> cleanupElements = new ArrayList<>();
    if (filtered.size() > 1) {
      final PsiMethodMember[] members = new PsiMethodMember[filtered.size()];
      int i = 0;
      for (PsiMethod constructor : filtered) {
        members[i++] = new PsiMethodMember(constructor);
      }
      final List<PsiMethodMember> elements;
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        elements = Arrays.asList(members);
      } else {
        final MemberChooser<PsiMethodMember> chooser = new MemberChooser<>(members, false, true, project);
        chooser.setTitle(QuickFixBundle.message("choose.constructors.to.add.parameter.to"));
        chooser.show();
        elements = chooser.getSelectedElements();
        if (elements == null) return;
      }

      for (PsiMethodMember member : elements) {
        if (!addParameterToConstructor(project, file, editor, member.getElement(), new PsiField[] {getField()}, cleanupElements)) {
          break;
        }
      }
    }
    else if (!filtered.isEmpty()) {
      final Collection<SmartPsiElementPointer<PsiField>> fieldsToFix = getFieldsToFix();
      try {
        final PsiMethod constructor = filtered.get(0);
        final LinkedHashSet<PsiField> fields = new LinkedHashSet<>();
        fieldsToFix.add(myField);
        for (SmartPsiElementPointer<PsiField> elementPointer : fieldsToFix) {
          final PsiField field = elementPointer.getElement();
          if (field != null && isAvailable(field) && filterConstructorsIfFieldAlreadyAssigned(new PsiMethod[]{constructor}, field).contains(constructor)) {
            fields.add(field);
          }
        }
        if (filtered.size() == constructors.length && fields.size() > 1 && !ApplicationManager.getApplication().isUnitTestMode()) {
          PsiFieldMember[] members = new PsiFieldMember[fields.size()];
          int i = 0;
          for (PsiField field : fields) {
            members[i++] = new PsiFieldMember(field);
          }
          MemberChooser<PsiElementClassMember> chooser = new MemberChooser<>(members, false, true, project);
          chooser.setTitle(QuickFixBundle.message("choose.fields.to.generate.constructor.parameters.for"));
          chooser.show();
          if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE) return;
          final List<PsiElementClassMember> selectedElements = chooser.getSelectedElements();
          if (selectedElements == null) return;
          fields.clear();
          for (PsiElementClassMember member : selectedElements) {
            fields.add((PsiField)member.getElement());
          }
        }

        addParameterToConstructor(project, file, editor, constructor, filtered.size() == constructors.length
                                                                      ? fields.toArray(PsiField.EMPTY_ARRAY)
                                                                      : new PsiField[]{getField()}, cleanupElements);
      }
      finally {
        fieldsToFix.clear();
      }
    }
    GlobalInspectionContextBase.cleanupElements(project, null, cleanupElements);
  }

  private static PsiMethod[] getNonSyntheticConstructors(@NotNull PsiClass psiClass) {
    return ContainerUtil.filter(psiClass.getConstructors(), c -> !(c instanceof SyntheticElement)).toArray(PsiMethod[]::new);
  }

  @NotNull
  private static List<PsiMethod> getFilteredConstructors(PsiMethod[] constructors, PsiField field) {
    Arrays.sort(constructors, new Comparator<>() {
      @Override
      public int compare(PsiMethod c1, PsiMethod c2) {
        final PsiMethod cc1 = CommonJavaRefactoringUtil.getChainedConstructor(c1);
        final PsiMethod cc2 = CommonJavaRefactoringUtil.getChainedConstructor(c2);
        if (cc1 == c2) return 1;
        if (cc2 == c1) return -1;
        if (cc1 == null) {
          return cc2 == null ? 0 : compare(c1, cc2);
        }
        else {
          return cc2 == null ? compare(cc1, c2) : compare(cc1, cc2);
        }
      }
    });
    return filterConstructorsIfFieldAlreadyAssigned(constructors, field);
  }

  @NotNull
  private Collection<SmartPsiElementPointer<PsiField>> getFieldsToFix() {
    Map<SmartPsiElementPointer<PsiField>, Boolean> fields = myClass.getUserData(FIELDS);
    if (fields == null) {
      fields = ((UserDataHolderEx)myClass).putUserDataIfAbsent(FIELDS, ContainerUtil.createConcurrentWeakMap());
    }
    final Map<SmartPsiElementPointer<PsiField>, Boolean> finalFields = fields;
    return new AbstractCollection<>() {
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

  static List<PsiMethod> filterConstructorsIfFieldAlreadyAssigned(PsiMethod[] constructors, PsiField field) {
    final List<PsiMethod> result = new ArrayList<>(Arrays.asList(constructors));
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
                                                   final PsiField[] fields,
                                                   final List<? super SmartPsiElementPointer<PsiElement>> cleanupElements) throws IncorrectOperationException {
    final PsiParameterList parameterList = constructor.getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();
    ParameterInfoImpl[] newParamInfos = new ParameterInfoImpl[parameters.length + fields.length];
    final List<PsiVariable> params = new ArrayList<>(Arrays.asList(parameters));
    Collections.addAll(params, fields);
    params.sort(new FieldParameterComparator(parameterList));

    int i = 0;
    final Map<PsiField, String> usedFields = new HashMap<>();
    final MultiMap<PsiType, PsiVariable> types = new MultiMap<>();
    for (PsiVariable param : params) {
      types.putValue(param.getType(), param);
    }

    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(file);
    final boolean preferLongerNames = settings.PREFER_LONGER_NAMES;
    for (PsiVariable param : params) {
      final PsiType paramType = param.getType();
      if (param instanceof PsiParameter) {
        newParamInfos[i++] = ParameterInfoImpl.create(parameterList.getParameterIndex((PsiParameter)param))
          .withName(param.getName())
          .withType(paramType)
          .withDefaultValue(param.getName());
      } else {
        try {
          settings.PREFER_LONGER_NAMES = preferLongerNames || types.get(paramType).size() > 1;
          final String uniqueParameterName = getUniqueParameterName(parameters, param, usedFields);
          usedFields.put((PsiField)param, uniqueParameterName);
          newParamInfos[i++] = ParameterInfoImpl.createNew()
            .withName(uniqueParameterName)
            .withType(AnnotationTargetUtil.keepStrictlyTypeUseAnnotations(param.getModifierList(), paramType))
            .withDefaultValue(uniqueParameterName);
        }
        finally {
          settings.PREFER_LONGER_NAMES = preferLongerNames;
        }
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
      ChangeMethodSignatureFromUsageFix.performChange(project, editor, file, constructor, minUsagesNumber, newParamInfos, true, true, (List<ParameterInfo> p) -> {
        final ParameterInfo[] resultParams = p.toArray(new ParameterInfo[0]);
        doCreate(project, editor, parameters, constructorPointer, resultParams, usedFields, cleanupElements);
      } );
    return parameterInfos != null;
  }

  private static String createDummyMethod(PsiMethod constructor, ParameterInfoImpl[] newParamInfos) {
    return constructor.getName() + "(" + StringUtil.join(newParamInfos, info -> info.getTypeText() + " " + info.getName(), ", ") + "){}";
  }

  private static String getUniqueParameterName(PsiParameter[] parameters, PsiVariable variable, Map<PsiField, String> usedNames) {
    String name = variable.getName();
    assert name != null : variable;
    JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(variable.getProject());
    name = styleManager.variableNameToPropertyName(name, VariableKind.FIELD);
    SuggestedNameInfo nameInfo = styleManager.suggestVariableName(VariableKind.PARAMETER, name, null, variable.getType());
    String newName = nameInfo.names[0];
    int n = 1;
    while (!isUnique(parameters, newName, usedNames)) {
      newName = n < nameInfo.names.length &&
                !JavaCodeStyleSettings.getInstance(variable.getContainingFile()).PREFER_LONGER_NAMES
                ? nameInfo.names[n++] : nameInfo.names[0] + n++;
    }
    return newName;
  }

  private static boolean isUnique(PsiParameter[] params, String newName, Map<PsiField, String> usedNames) {
    if (usedNames.containsValue(newName)) return false;
    for (PsiParameter parameter : params) {
      if (Comparing.strEqual(parameter.getName(), newName)) {
        return false;
      }
    }
    return true;
  }

  private static boolean doCreate(Project project, Editor editor, PsiParameter[] parameters, SmartPsiElementPointer constructorPointer,
                                  ParameterInfo[] parameterInfos, Map<PsiField, String> fields, List<? super SmartPsiElementPointer<PsiElement>> cleanupElements) {
    PsiMethod constructor = (PsiMethod)constructorPointer.getElement();
    assert constructor != null;
    PsiParameter[] newParameters = constructor.getParameterList().getParameters();
    if (newParameters == parameters) return false; //user must have canceled dialog
    // do not introduce assignment in chained constructor
    if (JavaHighlightUtil.getChainedConstructors(constructor).isEmpty()) {
      final SmartPointerManager manager = SmartPointerManager.getInstance(project);
      boolean created = false;
      for (Map.Entry<PsiField, String> entry : fields.entrySet()) {
        PsiField field = entry.getKey();
        final String defaultParamName = entry.getValue();
        PsiParameter parameter = findParamByName(defaultParamName, field.getType(), newParameters, parameterInfos);
        if (parameter == null) {
          continue;
        }
        NullableNotNullManager.getInstance(field.getProject()).copyNullableOrNotNullAnnotation(field, parameter);
        cleanupElements.add(manager.createSmartPsiElementPointer(parameter));
        final PsiElement assignmentStatement = AssignFieldFromParameterAction.addFieldAssignmentStatement(project, field, parameter, editor);
        if (assignmentStatement != null) {
          cleanupElements.add(manager.createSmartPsiElementPointer(assignmentStatement));
        }
        created = true;
      }
      return created;
    } else {
      return true;
    }
  }

  @Nullable
  private static PsiParameter findParamByName(String newName,
                                              PsiType type,
                                              PsiParameter[] newParameters,
                                              ParameterInfo[] parameterInfos) {
    for (PsiParameter newParameter : newParameters) {
      if (Comparing.strEqual(newName, newParameter.getName())) {
        return newParameter;
      }
    }
    for (int i = 0; i < newParameters.length; i++) {
      if (parameterInfos[i].isNew()) {
        final PsiParameter parameter = newParameters[i];
        final PsiType paramType = ((ParameterInfoImpl)parameterInfos[i]).getTypeWrapper().getType(parameter);
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

    FieldParameterComparator(PsiParameterList parameterList) {
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
