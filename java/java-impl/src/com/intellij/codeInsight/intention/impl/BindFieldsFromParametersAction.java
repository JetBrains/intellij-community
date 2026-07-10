// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.FinalUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public final class BindFieldsFromParametersAction implements ModCommandAction, DumbAware {
  private static final Logger LOG = Logger.getInstance(BindFieldsFromParametersAction.class);

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    if (!BaseIntentionAction.canModify(context.file())) return null;
    PsiParameter psiParameter = FieldFromParameterUtils.findParameterAtOffset(context.file(), context.offset());
    PsiMethod method = findMethod(psiParameter, context);
    if (method == null) return null;

    List<PsiParameter> parameters = getAvailableParameters(method);

    if (parameters.isEmpty()) return null;
    if (parameters.size() == 1 && psiParameter != null) return null;
    if (psiParameter != null && !parameters.contains(psiParameter)) return null;

    return Presentation.of(JavaBundle.message("intention.bind.fields.from.parameters.text", JavaElementKind.fromElement(method).object()))
      .withPriority(PriorityAction.Priority.HIGH);
  }

  private static @Nullable PsiMethod findMethod(@Nullable PsiParameter parameter, @NotNull ActionContext context) {
    if (parameter == null) {
      PsiElement elementAt = context.findLeaf();
      if (elementAt instanceof PsiIdentifier) {
        PsiElement parent = elementAt.getParent();
        if (parent instanceof PsiMethod method) {
          return method;
        }
      }
    }
    else {
      PsiElement declarationScope = parameter.getDeclarationScope();
      if (declarationScope instanceof PsiMethod method) {
        return method;
      }
    }

    return null;
  }

  private static @Unmodifiable @NotNull List<PsiParameter> getAvailableParameters(@NotNull PsiMethod method) {
    return ContainerUtil.filter(method.getParameterList().getParameters(), BindFieldsFromParametersAction::isAvailable);
  }

  private static boolean isAvailable(@NotNull PsiParameter psiParameter) {
    PsiType type = FieldFromParameterUtils.getSubstitutedType(psiParameter);
    if (type != null && !type.isAssignableFrom(psiParameter.getType())) return false;
    if (isPassedToChainedConstructor(psiParameter)) return false;
    PsiClass targetClass = PsiTreeUtil.getParentOfType(psiParameter, PsiClass.class);
    return FieldFromParameterUtils.isAvailable(psiParameter, type, targetClass) &&
           psiParameter.getLanguage().isKindOf(JavaLanguage.INSTANCE);
  }

  /**
   * Tells whether {@code parameter} is passed as an argument to the {@code super(...)}/{@code this(...)} call of its
   * constructor.
   */
  private static boolean isPassedToChainedConstructor(@NotNull PsiParameter parameter) {
    if (!(parameter.getDeclarationScope() instanceof PsiMethod method) || !method.isConstructor()) return false;
    PsiMethodCallExpression call = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method);
    if (call == null) return false;
    for (PsiExpression arg : call.getArgumentList().getExpressions()) {
      if (arg instanceof PsiReferenceExpression ref && ref.resolve() == parameter) {
        return true;
      }
    }
    return false;
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.bind.fields.from.parameters.family");
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    PsiParameter psiParameter = FieldFromParameterUtils.findParameterAtOffset(context.file(), context.offset());
    PsiMethod method = findMethod(psiParameter, context);
    LOG.assertTrue(method != null);

    HashSet<String> usedNames = new HashSet<>();
    List<PsiParameter> availableParameters = getAvailableParameters(method);

    return selectParameters(method, availableParameters, parameters -> ModCommand.psiUpdate(context, updater -> {
      MultiMap<PsiType, PsiParameter> types = new MultiMap<>();
      List<PsiParameter> writable = ContainerUtil.map(parameters, updater::getWritable);
      for (PsiParameter parameter : writable) {
        types.putValue(parameter.getType(), parameter);
      }
      CodeStyleSettings allSettings = CodeStyleSettingsManager.getInstance(context.project())
        .cloneSettings(CodeStyle.getSettings(context.file()));
      JavaCodeStyleSettings settings = allSettings.getCustomSettings(JavaCodeStyleSettings.class);

      boolean preferLongerNames = settings.PREFER_LONGER_NAMES;
      for (PsiParameter selected : writable) {
        settings.PREFER_LONGER_NAMES = preferLongerNames || types.get(selected.getType()).size() > 1;
        CodeStyle.runWithLocalSettings(context.project(), allSettings,
                                       () -> processParameter(context.project(), selected, usedNames));
      }
    }));
  }

  private static @NotNull ModCommand selectParameters(@NotNull PsiMethod method,
                                                      @NotNull List<PsiParameter> parameters,
                                                      @NotNull Function<List<PsiParameter>, ModCommand> function) {
    if (parameters.size() < 2) {
      return function.apply(parameters);
    }

    List<@NotNull ParameterClassMember> members = sortByParameterIndex(
      ContainerUtil.map(parameters, ParameterClassMember::new), method);
    // Parameters forwarded to a super()/this() call are already filtered out in isAvailable(), so every
    // available parameter is selected by default.
    return ModCommand.chooseMultipleMembers(
      JavaBundle.message("dialog.title.choose.0.parameters", method.isConstructor() ? "Constructor" : "Method"),
                               members,
                               members,
                               function.compose(elements -> ContainerUtil.map(elements, e -> ((ParameterClassMember)e).getParameter())));
  }

  private static @NotNull List<@NotNull ParameterClassMember> sortByParameterIndex(@NotNull List<@NotNull ParameterClassMember> members, @NotNull PsiMethod method) {
    PsiParameterList parameterList = method.getParameterList();
    return members.stream().sorted(Comparator.comparingInt(o -> parameterList.getParameterIndex(o.getParameter()))).toList();
  }

  private static void processParameter(@NotNull Project project, @NotNull PsiParameter parameter, @NotNull Set<String> usedNames) {
    PsiType type = FieldFromParameterUtils.getSubstitutedType(parameter);
    if (type == null) return;
    JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
    String parameterName = parameter.getName();
    String propertyName = styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER);

    if (!(parameter.getDeclarationScope() instanceof PsiMethod method)) return;
    PsiClass targetClass = method.getContainingClass();
    if (targetClass == null) return;

    boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);

    VariableKind kind = isMethodStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;
    SuggestedNameInfo suggestedNameInfo = styleManager.suggestVariableName(kind, propertyName, null, type);
    String[] names = suggestedNameInfo.names;

    String name = names[0];
    for (String curName : names) {
      if (!usedNames.contains(curName)) {
        PsiField fieldByName = targetClass.findFieldByName(curName, false);
        if (fieldByName != null &&
            (!method.isConstructor() || !isFieldAssigned(fieldByName, method)) &&
            fieldByName.getType().isAssignableFrom(parameter.getType())) {
          name = curName;
          break;
        }
      }
    }

    if (usedNames.contains(name)) {
      for (String curName : names) {
        if (!usedNames.contains(curName)) {
          name = curName;
          break;
        }
      }
    }
    String fieldName = usedNames.add(name) ? name : styleManager.suggestUniqueVariableName(name, parameter, true);

    boolean maybeFinal = !isMethodStatic && method.isConstructor();
    boolean isFinal = maybeFinal && targetClass.getConstructors().length == 1;
    PsiField field = FieldFromParameterUtils.createFieldAndAddAssignment(
      project, targetClass, method, parameter, type, fieldName, isMethodStatic, isFinal);
    if (field != null && maybeFinal && !isFinal && FinalUtils.canBeFinal(field)) {
      Objects.requireNonNull(field.getModifierList()).setModifierProperty(PsiModifier.FINAL, true);
    }
  }

  private static boolean isFieldAssigned(PsiField field, PsiMethod method) {
    return ReferencesSearch.search(field, new LocalSearchScope(method)).anyMatch(
      reference -> reference instanceof PsiReferenceExpression && PsiUtil.isOnAssignmentLeftHand((PsiReferenceExpression)reference));
  }
}
