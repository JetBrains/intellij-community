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
package com.intellij.compiler.classFilesIndex.chainsSearch.context;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public final class ContextUtil {
  @Nullable
  public static ChainCompletionContext createContext(final @Nullable PsiType variableType,
                                                     final @Nullable String variableName,
                                                     final @Nullable PsiElement containingElement) {
    if (variableType == null || containingElement == null) {
      return null;
    }

    final TargetType target;
    if (variableType instanceof PsiClassType) {
      target = TargetType.create((PsiClassType)variableType);
    }
    else if (variableType instanceof PsiArrayType) {
      target = TargetType.create((PsiArrayType)variableType);
    }
    else {
      return null;
    }
    if (target == null) {
      return null;
    }

    final PsiMethod method = PsiTreeUtil.getParentOfType(containingElement, PsiMethod.class);
    if (method == null) {
      return null;
    }
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) {
      return null;
    }
    final Set<String> containingClassQNames = resolveSupersNamesRecursively(aClass);

    final List<PsiVariable> contextVars = new SmartList<>();
    for (final PsiField field : aClass.getFields()) {
      final PsiClass containingClass = field.getContainingClass();
      if (containingClass != null) {
        if ((field.hasModifierProperty(PsiModifier.PUBLIC) ||
             field.hasModifierProperty(PsiModifier.PROTECTED) ||
             ((field.hasModifierProperty(PsiModifier.PRIVATE) || field.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) &&
              aClass.isEquivalentTo(containingClass))) && !field.getName().equals(variableName)) {
          contextVars.add(field);
        }
      }
    }
    Collections.addAll(contextVars, method.getParameterList().getParameters());

    final PsiCodeBlock methodBody = method.getBody();
    assert methodBody != null;
    boolean processMethodTail = false;
    final List<PsiElement> afterElements = new ArrayList<>();
    for (final PsiElement element : methodBody.getChildren()) {
      if (element.isEquivalentTo(containingElement)) {
        if (variableType instanceof PsiClassType) {
          processMethodTail = true;
          continue;
        }
        else {
          break;
        }
      }
      if (element instanceof PsiDeclarationStatement) {
        if (processMethodTail) {
          afterElements.add(element);
        }
        else {
          for (final PsiElement declaredElement : ((PsiDeclarationStatement)element).getDeclaredElements()) {
            if (declaredElement instanceof PsiLocalVariable &&
                (variableName == null || !variableName.equals(((PsiLocalVariable)declaredElement).getName()))) {
              contextVars.add((PsiVariable)declaredElement);
            }
          }
        }
      }
    }

    final Set<String> excludedQNames = processMethodTail
                                       ? generateExcludedQNames(afterElements, ((PsiClassType)variableType).resolve(), variableName,
                                                                contextVars)
                                       : Collections.<String>emptySet();

    final List<PsiMethod> contextMethods = new ArrayList<>();
    for (final PsiMethod psiMethod : aClass.getMethods()) {
      if ((psiMethod.hasModifierProperty(PsiModifier.PROTECTED) || psiMethod.hasModifierProperty(PsiModifier.PRIVATE)) &&
          psiMethod.getParameterList().getParametersCount() == 0) {
        contextMethods.add(psiMethod);
      }
    }

    return create(target, contextVars, contextMethods, containingClassQNames, containingElement.getProject(),
                  containingElement.getResolveScope(), excludedQNames);
  }

  private static Set<String> generateExcludedQNames(final List<PsiElement> tailElements,
                                                    final @Nullable PsiClass psiClass,
                                                    final @Nullable String varName,
                                                    final List<PsiVariable> contextVars) {
    if (psiClass == null) {
      return Collections.emptySet();
    }
    final String classQName = psiClass.getQualifiedName();
    if (classQName == null) {
      return Collections.emptySet();
    }

    final Set<String> excludedQNames = new HashSet<>();
    if (!tailElements.isEmpty()) {
      final Set<String> contextVarTypes = new HashSet<>();
      final Map<String, PsiVariable> contextVarNamesToVar = new HashMap<>();
      for (final PsiVariable var : contextVars) {
        contextVarTypes.add(var.getType().getCanonicalText());
        contextVarNamesToVar.put(var.getName(), var);
      }
      for (final PsiElement element : tailElements) {
        final Collection<PsiMethodCallExpression> methodCallExpressions =
          PsiTreeUtil.findChildrenOfType(element, PsiMethodCallExpression.class);
        for (final PsiMethodCallExpression methodCallExpression : methodCallExpressions) {
          final PsiExpressionList args = methodCallExpression.getArgumentList();
          final PsiMethod resolvedMethod = methodCallExpression.resolveMethod();
          if (resolvedMethod != null) {
            final PsiType returnType = resolvedMethod.getReturnType();
            if (returnType != null) {
              final String returnTypeAsString = returnType.getCanonicalText();
              for (final PsiExpression expression : args.getExpressions()) {
                final String qVarName = expression.getText();
                if (qVarName != null) {
                  if (contextVarNamesToVar.containsKey(qVarName) || qVarName.equals(varName)) {
                    excludedQNames.add(returnTypeAsString);
                  }
                }
              }
              if (!contextVarTypes.contains(returnTypeAsString)) {
                excludedQNames.add(returnTypeAsString);
              }
            }
          }
        }
      }
    }

    return excludedQNames;
  }

  @Nullable
  private static ChainCompletionContext create(final TargetType target,
                                               final List<PsiVariable> contextVars,
                                               final List<PsiMethod> contextMethods,
                                               final Set<String> containingClassQNames,
                                               final Project project,
                                               final GlobalSearchScope resolveScope,
                                               final Set<String> excludedQNames) {
    final MultiMap<String, PsiVariable> classQNameToVariable = new MultiMap<>();
    final MultiMap<String, PsiMethod> containingClassGetters = new MultiMap<>();
    final MultiMap<String, ContextRelevantVariableGetter> contextVarsGetters = new MultiMap<>();
    final Map<String, PsiVariable> stringVars = new HashMap<>();

    for (final PsiMethod method : contextMethods) {
      final PsiType returnType = method.getReturnType();
      if (returnType != null) {
        final String returnTypeQName = returnType.getCanonicalText();
        containingClassGetters.putValue(returnTypeQName, method);
      }
    }

    for (final PsiVariable var : contextVars) {
      final PsiType type = var.getType();
      final Set<String> classQNames = new HashSet<>();
      if (type instanceof PsiClassType) {
        if (JAVA_LANG_STRING_SHORT_NAME.equals(((PsiClassType)type).getClassName())) {
          final String varName = var.getName();
          if (varName != null) {
            stringVars.put(ChainCompletionContextStringUtil.sanitizedToLowerCase(varName), var);
            continue;
          }
        }

        final PsiClass aClass = ((PsiClassType)type).resolve();
        if (aClass != null) {
          final String classQName = type.getCanonicalText();
          if (!target.getClassQName().equals(classQName)) {
            classQNames.add(classQName);
            classQNames.addAll(resolveSupersNamesRecursively(aClass));
            for (final PsiMethod method : aClass.getAllMethods()) {
              if (method.getParameterList().getParametersCount() == 0 && method.getName().startsWith("get")) {
                final PsiType returnType = method.getReturnType();
                if (returnType != null) {
                  final String getterReturnTypeQName = returnType.getCanonicalText();
                  contextVarsGetters.putValue(getterReturnTypeQName, new ContextRelevantVariableGetter(var, method));
                }
              }
            }
          }
        }
      }
      else {
        final String classQName = type.getCanonicalText();
        classQNames.add(classQName);
      }
      for (final String qName : classQNames) {
        classQNameToVariable.putValue(qName, var);
      }
    }
    return new ChainCompletionContext(target, containingClassQNames, classQNameToVariable, containingClassGetters,
                                      contextVarsGetters, stringVars, excludedQNames, project, resolveScope);
  }

  @NotNull
  private static Set<String> resolveSupersNamesRecursively(@Nullable final PsiClass psiClass) {
    final Set<String> result = new HashSet<>();
    if (psiClass != null) {
      for (final PsiClass superClass : psiClass.getSupers()) {
        final String qualifiedName = superClass.getQualifiedName();
        if (!CommonClassNames.JAVA_LANG_OBJECT.equals(qualifiedName)) {
          if (qualifiedName != null) {
            result.add(qualifiedName);
          }
          result.addAll(resolveSupersNamesRecursively(superClass));
        }
      }
    }
    return result;
  }

  private final static String JAVA_LANG_STRING_SHORT_NAME = StringUtil.getShortName(CommonClassNames.JAVA_LANG_STRING);
}
