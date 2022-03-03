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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.ide.util.MemberChooser;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.JavaElementKind;
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
  private static final Logger LOG = Logger.getInstance(BindFieldsFromParametersAction.class);

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiParameter psiParameter = FieldFromParameterUtils.findParameterAtCursor(file, editor);
    PsiMethod method = findMethod(psiParameter, editor, file);
    if (method == null) return false;

    List<PsiParameter> parameters = getAvailableParameters(method);

    if (parameters.isEmpty()) return false;
    if (parameters.size() == 1 && psiParameter != null) return false;
    if (psiParameter != null && !parameters.contains(psiParameter)) return false;

    setText(JavaBundle.message("intention.bind.fields.from.parameters.text", JavaElementKind.fromElement(method).object()));
    return true;
  }

  @Nullable
  private static PsiMethod findMethod(@Nullable PsiParameter parameter, @NotNull Editor editor, @NotNull PsiFile file) {
    if (parameter == null) {
      PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset());
      if (elementAt instanceof PsiIdentifier) {
        PsiElement parent = elementAt.getParent();
        if (parent instanceof PsiMethod) {
          return (PsiMethod)parent;
        }
      }
    }
    else {
      PsiElement declarationScope = parameter.getDeclarationScope();
      if (declarationScope instanceof PsiMethod) {
        return (PsiMethod)declarationScope;
      }
    }

    return null;
  }

  @NotNull
  private static List<PsiParameter> getAvailableParameters(@NotNull PsiMethod method) {
    List<PsiParameter> parameters = new ArrayList<>();
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      if (isAvailable(parameter)) {
        parameters.add(parameter);
      }
    }
    return parameters;
  }

  private static boolean isAvailable(@NotNull PsiParameter psiParameter) {
    PsiType type = FieldFromParameterUtils.getSubstitutedType(psiParameter);
    PsiClass targetClass = PsiTreeUtil.getParentOfType(psiParameter, PsiClass.class);
    return FieldFromParameterUtils.isAvailable(psiParameter, type, targetClass) &&
           psiParameter.getLanguage().isKindOf(JavaLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.bind.fields.from.parameters.family");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    invoke(project, editor, file, !ApplicationManager.getApplication().isUnitTestMode());
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project,
                                                       @NotNull Editor editor,
                                                       @NotNull PsiFile file) {
    invoke(project, editor, file, false);
    return IntentionPreviewInfo.DIFF;
  }

  private static void invoke(Project project, Editor editor, PsiFile file, boolean isInteractive) {
    PsiParameter psiParameter = FieldFromParameterUtils.findParameterAtCursor(file, editor);
    if (file.isPhysical() && !FileModificationService.getInstance().prepareFileForWrite(file)) return;
    PsiMethod method = psiParameter != null
                       ? (PsiMethod)psiParameter.getDeclarationScope()
                       : PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PsiMethod.class);
    LOG.assertTrue(method != null);

    HashSet<String> usedNames = new HashSet<>();
    List<PsiParameter> availableParameters = getAvailableParameters(method);
    Iterable<PsiParameter> parameters = selectParameters(project, method, availableParameters, isInteractive);
    MultiMap<PsiType, PsiParameter> types = new MultiMap<>();
    for (PsiParameter parameter : parameters) {
      types.putValue(parameter.getType(), parameter);
    }
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(file);
    boolean preferLongerNames = settings.PREFER_LONGER_NAMES;
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
  private static List<PsiParameter> selectParameters(@NotNull Project project,
                                                     @NotNull PsiMethod method,
                                                     @NotNull List<PsiParameter> parameters,
                                                     boolean isInteractive) {
    if (parameters.size() < 2 || !isInteractive) {
      return parameters;
    }

    ParameterClassMember[] members = sortByParameterIndex(
      ContainerUtil.map2Array(parameters, ParameterClassMember.EMPTY_ARRAY, ParameterClassMember::new), method);

    MemberChooser<ParameterClassMember> chooser = showChooser(project, method, members);

    List<ParameterClassMember> selectedElements = chooser.getSelectedElements();
    if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE || selectedElements == null) {
      return Collections.emptyList();
    }

    return ContainerUtil.map(selectedElements, ParameterClassMember::getParameter);
  }

  @NotNull
  private static MemberChooser<ParameterClassMember> showChooser(@NotNull Project project,
                                           @NotNull PsiMethod method,
                                           ParameterClassMember @NotNull [] members) {
    MemberChooser<ParameterClassMember> chooser = new MemberChooser<>(members, false, true, project);
    chooser.selectElements(getInitialSelection(method, members));
    chooser.setTitle(JavaBundle.message("dialog.title.choose.0.parameters", method.isConstructor() ? "Constructor" : "Method"));
    chooser.show();
    return chooser;
  }

  /**
   * Exclude parameters passed to super() or this() calls from initial selection
   */
  private static ParameterClassMember[] getInitialSelection(@NotNull PsiMethod method,
                                                            ParameterClassMember @NotNull [] members) {
    Set<PsiElement> resolvedInSuperOrThis = new HashSet<>();
    PsiCodeBlock body = method.getBody();
    LOG.assertTrue(body != null);
    PsiStatement[] statements = body.getStatements();
    if (statements.length > 0 && statements[0] instanceof PsiExpressionStatement) {
      PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
      if (expression instanceof PsiMethodCallExpression) {
        PsiMethod calledMethod = ((PsiMethodCallExpression)expression).resolveMethod();
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

  private static ParameterClassMember @NotNull [] sortByParameterIndex(ParameterClassMember @NotNull [] members, @NotNull PsiMethod method) {
    PsiParameterList parameterList = method.getParameterList();
    Arrays.sort(members, Comparator.comparingInt(o -> parameterList.getParameterIndex(o.getParameter())));
    return members;
  }

  private static void processParameter(@NotNull Project project,
                                       @NotNull PsiParameter parameter,
                                       @NotNull Set<String> usedNames) {
    PsiType type = FieldFromParameterUtils.getSubstitutedType(parameter);
    JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
    String parameterName = parameter.getName();
    String propertyName = styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER);

    PsiClass targetClass = PsiTreeUtil.getParentOfType(parameter, PsiClass.class);
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (!(declarationScope instanceof PsiMethod)) return;
    PsiMethod method = (PsiMethod)declarationScope;

    boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);

    VariableKind kind = isMethodStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;
    SuggestedNameInfo suggestedNameInfo = styleManager.suggestVariableName(kind, propertyName, null, type);
    String[] names = suggestedNameInfo.names;

    boolean isFinal = !isMethodStatic && method.isConstructor();
    String name = names[0];
    if (targetClass != null) {
      for (String curName : names) {
        if (!usedNames.contains(curName)) {
          PsiField fieldByName = targetClass.findFieldByName(curName, false);
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
    
    String fieldName = usedNames.add(name) ? name : JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName(name, parameter, true);

    Runnable runnable = () -> {
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
    };
    if (parameter.isPhysical()) {
      ApplicationManager.getApplication().runWriteAction(runnable);
    } else {
      runnable.run();
    }
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
