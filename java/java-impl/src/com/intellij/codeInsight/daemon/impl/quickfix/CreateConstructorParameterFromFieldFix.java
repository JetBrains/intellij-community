// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.codeInsight.intention.impl.AssignFieldFromParameterAction;
import com.intellij.codeInsight.intention.impl.FieldFromParameterUtils;
import com.intellij.modcommand.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CreateConstructorParameterFromFieldFix extends PsiBasedModCommandAction<PsiField> {
  public CreateConstructorParameterFromFieldFix(@NotNull PsiField field) {
    super(field);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiField field) {
    if (field.hasModifierProperty(PsiModifier.STATIC)) return null;
    PsiClass psiClass = field.getContainingClass();
    if (psiClass == null || psiClass instanceof PsiSyntheticClass || psiClass.isRecord() || psiClass.getName() == null) return null;
    if (psiClass.getConstructors().length <= 1 && getFieldsToFix(psiClass, field, List.of()).size() > 1) {
      return Presentation.of(QuickFixBundle.message("add.constructor.parameters"));
    }
    return Presentation.of(QuickFixBundle.message("add.constructor.parameter.name"));
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("add.constructor.parameters");
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiField field) {
    PsiClass psiClass = Objects.requireNonNull(field.getContainingClass());
    final List<PsiMethod> filtered = getFilteredConstructors(psiClass.getConstructors(), field);
    if (filtered.size() <= 1) {
      return performForConstructors(context, field, filtered);
    }
    List<PsiMethodMember> members = ContainerUtil.map(filtered, PsiMethodMember::new);
    return ModCommand.chooseMultipleMembers(QuickFixBundle.message("choose.constructors.to.add.parameter.to"), members,
                                            selected -> performForConstructors(context, field, ContainerUtil.map(selected,
                                                                                                                 member -> ((PsiMethodMember)member).getElement())));
  }

  private static @NotNull ModCommand performForConstructors(@NotNull ActionContext context,
                                                            @NotNull PsiField field,
                                                            @NotNull List<PsiMethod> constructors) {
    if (!field.isValid() || ContainerUtil.exists(constructors, c -> !c.isValid())) return ModCommand.nop();
    PsiClass psiClass = field.getContainingClass();
    if (psiClass == null) return ModCommand.nop();
    List<PsiField> allFields = getFieldsToFix(psiClass, field, constructors);
    if (allFields.isEmpty()) return ModCommand.nop();
    if (allFields.size() == 1) return performForConstructorsAndFields(context, allFields, constructors);
    List<PsiFieldMember> members = ContainerUtil.map(allFields, PsiFieldMember::new);
    return ModCommand.chooseMultipleMembers(QuickFixBundle.message("choose.fields.to.generate.constructor.parameters.for"),
                                            members,
                                            selected -> performForConstructorsAndFields(
                                              context, ContainerUtil.map(selected, member -> ((PsiFieldMember)member).getElement()),
                                              constructors));
  }

  private static @NotNull List<PsiMethod> getFilteredConstructors(PsiMethod[] constructors, PsiField field) {
    Arrays.sort(constructors, new Comparator<>() {
      @Override
      public int compare(PsiMethod c1, PsiMethod c2) {
        PsiMethod cc1 = CommonJavaRefactoringUtil.getChainedConstructor(c1);
        if (cc1 == c1) {
          cc1 = null;
        }
        PsiMethod cc2 = CommonJavaRefactoringUtil.getChainedConstructor(c2);
        if (cc2 == c2) {
          cc2 = null;
        }
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

  static @NotNull List<PsiField> getFieldsToFix(@NotNull PsiClass psiClass, @NotNull PsiField startField, @NotNull List<PsiMethod> constructors) {
    List<PsiField> fields = new ArrayList<>();
    for (PsiField field : psiClass.getFields()) {
      if (field == startField ||
          (!field.hasModifierProperty(PsiModifier.STATIC) &&
           field.hasModifierProperty(PsiModifier.FINAL) &&
           !ControlFlowUtil.isFieldInitializedAfterObjectConstruction(field) &&
           (constructors.isEmpty() || ContainerUtil.exists(constructors, ctr -> !isFieldAssignedInConstructor(field, ctr))))) {
        fields.add(field);
      }
    }
    return fields;
  }

  static List<PsiMethod> filterConstructorsIfFieldAlreadyAssigned(PsiMethod[] constructors, PsiField field) {
    final List<PsiMethod> result = new ArrayList<>(Arrays.asList(constructors));
    result.removeIf(ctr -> isFieldAssignedInConstructor(field, ctr));
    return result;
  }
  
  private static @NotNull PsiMethod getTargetConstructor(@NotNull PsiMethod constructor) {
    Set<PsiMethod> visited = null;
    while (true) {
      PsiMethodCallExpression constructorCall = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor);
      if (constructorCall == null || JavaPsiConstructorUtil.isSuperConstructorCall(constructorCall)) return constructor;
      PsiMethod target = constructorCall.resolveMethod();
      if (target == null) return constructor;
      if (visited == null) {
        visited = new HashSet<>();
      }
      if (!visited.add(target)) {
        return constructor;
      }
      constructor = target;
    }
  }

  private static boolean isFieldAssignedInConstructor(@NotNull PsiField field, @NotNull PsiMethod ctr) {
    return ctr instanceof SyntheticElement || VariableAccessUtils.variableIsAssigned(field, getTargetConstructor(ctr));
  }

  private static @NotNull ModCommand performForConstructorsAndFields(ActionContext context, List<PsiField> fields, List<PsiMethod> constructors) {
    return ModCommand.psiUpdate(context, updater -> {
      List<PsiField> writableFields = ContainerUtil.map(fields, updater::getWritable);
      PsiClass psiClass = writableFields.get(0).getContainingClass();
      if (psiClass == null) return;
      List<PsiMethod> writableConstructors;
      if (constructors.isEmpty()) {
        writableConstructors = List.of(AddDefaultConstructorFix.addDefaultConstructor(psiClass));
      }
      else {
        writableConstructors = ContainerUtil.map(constructors, updater::getWritable);
      }
      for (PsiMethod constructor : writableConstructors) {
        updater.trackDeclaration(constructor);
      }
      Map<PsiMethod, ChainedConstructorData> data = ChainedConstructorData.getChainedConstructorDataMap(writableFields, writableConstructors);
      Map<PsiMethod, List<PsiVariable>> params = StreamEx.of(writableConstructors)
        .toMap(ctr -> fillVariables(writableFields, ctr.getParameterList()));
      for (PsiMethod constructor : writableConstructors) {
        addParameterToConstructor(context, constructor, updater, data.get(constructor), params.get(constructor));
      }
    });
  }

  private static void addParameterToConstructor(@NotNull ActionContext context, @NotNull PsiMethod constructor,
                                                @NotNull ModPsiUpdater updater, @Nullable ChainedConstructorData chainedConstructorData, 
                                                @NotNull List<PsiVariable> params) {
    final PsiParameterList parameterList = constructor.getParameterList();

    final Map<PsiField, String> usedFields = new LinkedHashMap<>();
    final MultiMap<PsiType, PsiVariable> types = new MultiMap<>();
    for (PsiVariable param : params) {
      types.putValue(param.getType(), param);
    }

    Project project = context.project();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    CodeStyleSettings allSettings = CodeStyleSettingsManager.getInstance(project).cloneSettings(CodeStyle.getSettings(context.file()));
    JavaCodeStyleSettings settings = allSettings.getCustomSettings(JavaCodeStyleSettings.class);
    boolean preferLongerNames = settings.PREFER_LONGER_NAMES;
    Ref<PsiElement> prev = Ref.create();
    for (PsiVariable param : params) {
      final PsiType paramType = param.getType();
      if (param instanceof PsiField field) {
        settings.PREFER_LONGER_NAMES = preferLongerNames || types.get(paramType).size() > 1;
        CodeStyle.runWithLocalSettings(project, allSettings, () -> {
          final String uniqueParameterName = getUniqueParameterName(parameterList.getParameters(), param, usedFields);
          usedFields.put(field, uniqueParameterName);
          PsiType type = AnnotationTargetUtil.keepStrictlyTypeUseAnnotations(param.getModifierList(), paramType);
          PsiParameter parameter = factory.createParameter(uniqueParameterName, type, parameterList);
          if (settings.GENERATE_FINAL_PARAMETERS) {
            PsiModifierList modifierList = parameter.getModifierList();
            assert modifierList != null;
            modifierList.setModifierProperty(PsiModifier.FINAL, true);
          }
          if (prev.isNull()) {
            prev.set(parameterList.isEmpty() ? parameterList.add(parameter) :
                     parameterList.addBefore(parameter, parameterList.getParameter(0)));
          }
          else {
            prev.set(parameterList.addAfter(parameter, prev.get()));
          }
        });
      } else {
        prev.set(param);
      }
    }
    if (chainedConstructorData != null) {
      chainedConstructorData.updateChainedCall(constructor, usedFields);
    } else {
      PsiParameter[] newParameters = constructor.getParameterList().getParameters();
      // do not introduce assignment in chained constructor
      for (Map.Entry<PsiField, String> entry : usedFields.entrySet()) {
        PsiField field = entry.getKey();
        final String parameterName = entry.getValue();
        PsiParameter parameter = findParamByName(parameterName, newParameters);
        if (parameter == null) continue;
        NullableNotNullManager.getInstance(field.getProject()).copyNullableOrNotNullAnnotation(field, parameter);
        PsiStatement assignment = AssignFieldFromParameterAction.addFieldAssignmentStatement(project, field, parameter, updater);
        if (assignment != null) {
          CodeStyleManager.getInstance(project).reformat(assignment);
        }
      }
    }
  }

  private static @NotNull List<PsiVariable> fillVariables(@NotNull List<PsiField> fields, @NotNull PsiParameterList parameterList) {
    final List<PsiVariable> params = new ArrayList<>(Arrays.asList(parameterList.getParameters()));
    PsiMethod constructor = (PsiMethod)parameterList.getParent();
    for (PsiField field : fields) {
      if (!isFieldAssignedInConstructor(field, constructor)) {
        params.add(field);
      }
    }
    params.sort(new FieldParameterComparator(parameterList));
    return params;
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

  private static @Nullable PsiParameter findParamByName(@NotNull String newName, @NotNull PsiParameter @NotNull [] newParameters) {
    for (PsiParameter newParameter : newParameters) {
      if (Comparing.strEqual(newName, newParameter.getName())) {
        return newParameter;
      }
    }
    return null;
  }

  record ChainedConstructorData(@NotNull PsiMethodCallExpression methodCall, @NotNull List<PsiVariable> thisCallVariables) {
    private static @NotNull Map<PsiMethod, ChainedConstructorData> getChainedConstructorDataMap(List<PsiField> fields, List<PsiMethod> writableConstructors) {
      Map<PsiMethod, ChainedConstructorData> data = new HashMap<>();
      for (PsiMethod constructor : writableConstructors) {
        PsiMethodCallExpression methodCall = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor);
        if (methodCall != null) {
          PsiMethod target = methodCall.resolveMethod();
          if (target != null && writableConstructors.contains(target)) {
            data.put(constructor, new ChainedConstructorData(methodCall, fillVariables(fields, target.getParameterList())));
          }
        }
      }
      return data;
    }

    private void updateChainedCall(@NotNull PsiMethod constructor, @NotNull Map<PsiField, String> usedFields) {
      int index = 0;
      PsiExpressionList argumentList = methodCall.getArgumentList();
      PsiExpression[] args = argumentList.getExpressions();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(constructor.getProject());
      for (PsiVariable variable : thisCallVariables) {
        if (variable instanceof PsiParameter) {
          index++;
        } else if (variable instanceof PsiField field) {
          String parameterName = usedFields.get(field);
          PsiExpression arg = factory.createExpressionFromText(parameterName, constructor);
          argumentList.addAfter(arg, index == 0 ? null : args[index - 1]);
        }
      }
    }
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
