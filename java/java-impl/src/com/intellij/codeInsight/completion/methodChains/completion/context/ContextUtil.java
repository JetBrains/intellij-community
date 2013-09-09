package com.intellij.codeInsight.completion.methodChains.completion.context;

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
public class ContextUtil {
  @Nullable
  public static ChainCompletionContext createContext(final @Nullable PsiType variableType,
                                                     final @Nullable String variableName,
                                                     final @Nullable PsiElement containingElement) {
    if (variableType == null || containingElement == null) {
      return null;
    }
    if (variableType instanceof PsiClassType) {
      final PsiClass aClass = ((PsiClassType)variableType).resolve();
      if (aClass != null) {
        if (aClass.hasTypeParameters()) {
          return null;
        }
      }
      else {
        return null;
      }
    }

    final String targetQName = variableType.getCanonicalText();
    if (targetQName == null || targetQName.endsWith("[]")) {
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

    final List<PsiVariable> contextVars = new SmartList<PsiVariable>();
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
    final List<PsiElement> afterElements = new ArrayList<PsiElement>();
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

    final List<PsiMethod> contextMethods = new ArrayList<PsiMethod>();
    for (final PsiMethod psiMethod : aClass.getMethods()) {
      if ((psiMethod.hasModifierProperty(PsiModifier.PROTECTED) || psiMethod.hasModifierProperty(PsiModifier.PRIVATE)) &&
          psiMethod.getParameterList().getParametersCount() == 0) {
        contextMethods.add(psiMethod);
      }
    }

    return create(method, targetQName, contextVars, contextMethods, containingClassQNames, containingElement.getProject(),
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

    final Set<String> excludedQNames = new HashSet<String>();
    if (!tailElements.isEmpty()) {
      final Set<String> contextVarTypes = new HashSet<String>();
      final Map<String, PsiVariable> contextVarNamesToVar = new HashMap<String, PsiVariable>();
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
  private static ChainCompletionContext create(final PsiMethod contextMethod,
                                               final String targetQName,
                                               final List<PsiVariable> contextVars,
                                               final List<PsiMethod> contextMethods,
                                               final Set<String> containingClassQNames,
                                               final Project project,
                                               final GlobalSearchScope resolveScope,
                                               final Set<String> excludedQNames) {
    final MultiMap<String, PsiVariable> classQNameToVariable = new MultiMap<String, PsiVariable>();
    final MultiMap<String, PsiMethod> containingClassGetters = new MultiMap<String, PsiMethod>();
    final MultiMap<String, ContextRelevantVariableGetter> contextVarsGetters = new MultiMap<String, ContextRelevantVariableGetter>();
    final Map<String, PsiVariable> stringVars = new HashMap<String, PsiVariable>();

    for (final PsiMethod method : contextMethods) {
      PsiType returnType = method.getReturnType();
      if (returnType != null) {
        final String returnTypeQName = returnType.getCanonicalText();
        if (returnTypeQName != null) {
          containingClassGetters.putValue(returnTypeQName, method);
        }
      }
    }

    for (final PsiVariable var : contextVars) {
      final PsiType type = var.getType();
      final Set<String> classQNames = new HashSet<String>();
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
          if (!targetQName.equals(classQName)) {
            classQNames.add(classQName);
            classQNames.addAll(resolveSupersNamesRecursively(aClass));
            for (final PsiMethod method : aClass.getAllMethods()) {
              if (method.getParameterList().getParametersCount() == 0 && method.getName().startsWith("get")) {
                final String getterReturnTypeQName = method.getReturnType().getCanonicalText();
                if (getterReturnTypeQName != null) {
                  contextVarsGetters.putValue(getterReturnTypeQName, new ContextRelevantVariableGetter(var, method));
                }
              }
            }
          }
        }
      }
      else {
        final String classQName = type.getCanonicalText();
        if (classQName != null) {
          classQNames.add(classQName);
        }
      }
      for (final String qName : classQNames) {
        classQNameToVariable.putValue(qName, var);
      }
    }
    return new ChainCompletionContext(contextMethod, targetQName, containingClassQNames, classQNameToVariable, containingClassGetters,
                                      contextVarsGetters, stringVars, excludedQNames, project, resolveScope);
  }

  @NotNull
  private static Set<String> resolveSupersNamesRecursively(@Nullable final PsiClass psiClass) {
    final Set<String> result = new HashSet<String>();
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
