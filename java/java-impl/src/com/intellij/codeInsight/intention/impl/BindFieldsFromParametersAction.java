/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.ide.util.MemberChooser;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Danila Ponomarenko
 */
public class BindFieldsFromParametersAction extends BaseIntentionAction implements HighPriorityAction {
  private static final Logger LOG = Logger.getInstance(CreateFieldFromParameterAction.class);
  private static final Key<Map<SmartPsiElementPointer<PsiParameter>, Boolean>> PARAMS = Key.create("FIELDS_FROM_PARAMS");

  private static final Object LOCK = new Object();

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiParameter psiParameter = FieldFromParameterUtils.findParameterAtCursor(file, editor);
    PsiMethod method = findMethod(psiParameter, editor, file);
    if (method == null) return false;

    final List<PsiParameter> parameters = getAvailableParameters(method);

    synchronized (LOCK) {
      final Collection<SmartPsiElementPointer<PsiParameter>> params = getUnboundedParams(method);
      params.clear();
      for (PsiParameter parameter : parameters) {
        params.add(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(parameter));
      }
      if (params.isEmpty()) return false;
      if (params.size() == 1 && psiParameter != null) return false;
      if (psiParameter == null) {
        psiParameter = params.iterator().next().getElement();
        LOG.assertTrue(psiParameter != null);
      }

      setText(CodeInsightBundle.message("intention.bind.fields.from.parameters.text", method.isConstructor() ? "constructor" : "method"));
    }
    return isAvailable(psiParameter);
  }

  @Nullable
  private static PsiMethod findMethod(@Nullable PsiParameter parameter, @NotNull Editor editor, @NotNull PsiFile file) {
    if (parameter == null) {
      final PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset());
      if (elementAt instanceof PsiIdentifier) {
        final PsiElement parent = elementAt.getParent();
        if (parent instanceof PsiMethod) {
          return (PsiMethod)parent;
        }
      }
    }
    else {
      final PsiElement declarationScope = parameter.getDeclarationScope();
      if (declarationScope instanceof PsiMethod) {
        return (PsiMethod)declarationScope;
      }
    }

    return null;
  }

  @NotNull
  private static List<PsiParameter> getAvailableParameters(@NotNull PsiMethod method) {
    final List<PsiParameter> parameters = new ArrayList<>();
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      if (isAvailable(parameter)) {
        parameters.add(parameter);
      }
    }
    return parameters;
  }

  private static boolean isAvailable(PsiParameter psiParameter) {
    final PsiType type = FieldFromParameterUtils.getSubstitutedType(psiParameter);
    final PsiClass targetClass = PsiTreeUtil.getParentOfType(psiParameter, PsiClass.class);
    return FieldFromParameterUtils.isAvailable(psiParameter, type, targetClass) &&
           psiParameter.getLanguage().isKindOf(JavaLanguage.INSTANCE);
  }

  @NotNull
  private static Collection<SmartPsiElementPointer<PsiParameter>> getUnboundedParams(PsiMethod psiMethod) {
    Map<SmartPsiElementPointer<PsiParameter>, Boolean> params = psiMethod.getUserData(PARAMS);
    if (params == null) psiMethod.putUserData(PARAMS, params = ContainerUtil.createConcurrentWeakMap());
    final Map<SmartPsiElementPointer<PsiParameter>, Boolean> finalParams = params;
    return new AbstractCollection<SmartPsiElementPointer<PsiParameter>>() {
      @Override
      public boolean add(SmartPsiElementPointer<PsiParameter> psiVariable) {
        return finalParams.put(psiVariable, Boolean.TRUE) == null;
      }

      @Override
      public Iterator<SmartPsiElementPointer<PsiParameter>> iterator() {
        return finalParams.keySet().iterator();
      }

      @Override
      public int size() {
        return finalParams.size();
      }

      @Override
      public void clear() {
        finalParams.clear();
      }
    };
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.bind.fields.from.parameters.family");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    invoke(project, editor, file, !ApplicationManager.getApplication().isUnitTestMode());
  }

  private static void invoke(final Project project, Editor editor, PsiFile file, boolean isInteractive) {
    PsiParameter psiParameter = FieldFromParameterUtils.findParameterAtCursor(file, editor);
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    final PsiMethod method = psiParameter != null ? (PsiMethod)psiParameter.getDeclarationScope() : PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PsiMethod.class);
    LOG.assertTrue(method != null);

    final HashSet<String> usedNames = new HashSet<>();
    final Iterable<PsiParameter> parameters = selectParameters(project, method, copyUnboundedParamsAndClearOriginal(method), isInteractive);
    final MultiMap<PsiType, PsiParameter> types = new MultiMap<>();
    for (PsiParameter parameter : parameters) {
      types.putValue(parameter.getType(), parameter);
    }
    final JavaCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class);
    final boolean preferLongerNames = settings.PREFER_LONGER_NAMES;
    for (PsiParameter selected : parameters) {
      try {
        settings.PREFER_LONGER_NAMES = preferLongerNames || types.get(selected.getType()).size() > 1;
        processParameter(project, selected, usedNames);
      } finally {
        settings.PREFER_LONGER_NAMES = preferLongerNames;
      }
    }
  }

  @NotNull
  private static Iterable<PsiParameter> selectParameters(@NotNull Project project,
                                                         @NotNull PsiMethod method,
                                                         @NotNull Collection<SmartPsiElementPointer<PsiParameter>> unboundedParams,
                                                         boolean isInteractive) {
    if (unboundedParams.size() < 2 || !isInteractive) {
      return revealPointers(unboundedParams);
    }

    final ParameterClassMember[] members = sortByParameterIndex(toClassMemberArray(unboundedParams), method);

    final MemberChooser<ParameterClassMember> chooser = showChooser(project, method, members);

    final List<ParameterClassMember> selectedElements = chooser.getSelectedElements();
    if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE || selectedElements == null) {
      return Collections.emptyList();
    }

    return revealParameterClassMembers(selectedElements);
  }

  @NotNull
  private static MemberChooser<ParameterClassMember> showChooser(@NotNull Project project,
                                           @NotNull PsiMethod method,
                                           @NotNull ParameterClassMember[] members) {
    final MemberChooser<ParameterClassMember> chooser = new MemberChooser<>(members, false, true, project);
    chooser.selectElements(getInitialSelection(method, members));
    chooser.setTitle("Choose " + (method.isConstructor() ? "Constructor" : "Method") + " Parameters");
    chooser.show();
    return chooser;
  }

  /**
   * Exclude parameters passed to super() or this() calls from initial selection
   */
  private static ParameterClassMember[] getInitialSelection(@NotNull PsiMethod method,
                                                            @NotNull ParameterClassMember[] members) {
    final Set<PsiElement> resolvedInSuperOrThis = new HashSet<>();
    final PsiCodeBlock body = method.getBody();
    LOG.assertTrue(body != null);
    final PsiStatement[] statements = body.getStatements();
    if (statements.length > 0 && statements[0] instanceof PsiExpressionStatement) {
      final PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
      if (expression instanceof PsiMethodCallExpression) {
        final PsiMethod calledMethod = ((PsiMethodCallExpression)expression).resolveMethod();
        if (calledMethod != null && calledMethod.isConstructor()) {
          for (PsiExpression arg : ((PsiMethodCallExpression)expression).getArgumentList().getExpressions()) {
            if (arg instanceof PsiReferenceExpression) {
              ContainerUtil.addIfNotNull(resolvedInSuperOrThis, ((PsiReferenceExpression)arg).resolve());
            }
          }
        }
      }
    }
    return ContainerUtil.findAll(members, member -> !resolvedInSuperOrThis.contains(member.getParameter())).toArray(ParameterClassMember.EMPTY_ARRAY);
  }

  @NotNull
  private static ParameterClassMember[] sortByParameterIndex(@NotNull ParameterClassMember[] members, @NotNull PsiMethod method) {
    final PsiParameterList parameterList = method.getParameterList();
    Arrays.sort(members, (o1, o2) -> parameterList.getParameterIndex(o1.getParameter()) -
                                 parameterList.getParameterIndex(o2.getParameter()));
    return members;
  }

  @NotNull
  private static <T extends PsiElement> List<T> revealPointers(@NotNull Iterable<SmartPsiElementPointer<T>> pointers) {
    final List<T> result = new ArrayList<>();
    for (SmartPsiElementPointer<T> pointer : pointers) {
      result.add(pointer.getElement());
    }
    return result;
  }

  @NotNull
  private static List<PsiParameter> revealParameterClassMembers(@NotNull Iterable<ParameterClassMember> parameterClassMembers) {
    final List<PsiParameter> result = new ArrayList<>();
    for (ParameterClassMember parameterClassMember : parameterClassMembers) {
      result.add(parameterClassMember.getParameter());
    }
    return result;
  }

  @NotNull
  private static ParameterClassMember[] toClassMemberArray(@NotNull Collection<SmartPsiElementPointer<PsiParameter>> unboundedParams) {
    final ParameterClassMember[] result = new ParameterClassMember[unboundedParams.size()];
    int i = 0;
    for (SmartPsiElementPointer<PsiParameter> pointer : unboundedParams) {
      result[i++] = new ParameterClassMember(pointer.getElement());
    }
    return result;
  }

  @NotNull
  private static Collection<SmartPsiElementPointer<PsiParameter>> copyUnboundedParamsAndClearOriginal(@NotNull PsiMethod method) {
    synchronized (LOCK) {
      final Collection<SmartPsiElementPointer<PsiParameter>> unboundedParams = getUnboundedParams(method);
      final Collection<SmartPsiElementPointer<PsiParameter>> result = new ArrayList<>(unboundedParams);
      unboundedParams.clear();
      return result;
    }
  }

  private static void processParameter(final Project project,
                                       final PsiParameter parameter,
                                       final Set<String> usedNames) {
    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();
    final PsiType type = FieldFromParameterUtils.getSubstitutedType(parameter);
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
    final String parameterName = parameter.getName();
    String propertyName = styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER);

    final PsiClass targetClass = PsiTreeUtil.getParentOfType(parameter, PsiClass.class);
    final PsiElement declarationScope = parameter.getDeclarationScope();
    if (!(declarationScope instanceof PsiMethod)) return;
    final PsiMethod method = (PsiMethod)declarationScope;

    final boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);

    VariableKind kind = isMethodStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;
    SuggestedNameInfo suggestedNameInfo = styleManager.suggestVariableName(kind, propertyName, null, type);
    String[] names = suggestedNameInfo.names;

    final boolean isFinal = !isMethodStatic && method.isConstructor();
    String name = names[0];
    if (targetClass != null) {
      for (String curName : names) {
        if (!usedNames.contains(curName)) {
          final PsiField fieldByName = targetClass.findFieldByName(curName, false);
          if (fieldByName != null && (!method.isConstructor() || !isFieldAssigned(fieldByName, method)) && fieldByName.getType().isAssignableFrom(parameter.getType())) {
            name = curName;
            break;
          }
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
    
    final String fieldName = usedNames.add(name) ? name
                                                 : JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName(name, parameter, true);

    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        FieldFromParameterUtils.createFieldAndAddAssignment(
          project,
          targetClass,
          method,
          parameter,
          type,
          fieldName,
          isMethodStatic,
          isFinal);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    });
  }

  private static boolean isFieldAssigned(PsiField field, PsiMethod method) {
    for (PsiReference reference : ReferencesSearch.search(field, new LocalSearchScope(method))) {
      if (reference instanceof PsiReferenceExpression && PsiUtil.isOnAssignmentLeftHand((PsiReferenceExpression)reference)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
