// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.naming;

import com.intellij.codeInsight.generation.GetterSetterPrototypeProvider;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AutomaticGetterSetterRenamer extends AutomaticRenamer {

  private static final Logger LOG = Logger.getInstance(AutomaticGetterSetterRenamerFactory.class);

  public AutomaticGetterSetterRenamer(PsiElement element, String newName) {
    Map<PsiElement, String> allRenames = new LinkedHashMap<>();
    prepareFieldRenaming(((PsiField)element), newName, allRenames);
    for (Map.Entry<PsiElement, String> entry : allRenames.entrySet()) {
      myElements.add((PsiNamedElement)entry.getKey());
      suggestAllNames(((PsiNamedElement)entry.getKey()).getName(), entry.getValue());
    }
  }

  @Override
  public boolean isSelectedByDefault() {
    return true;
  }

  @Override
  public boolean allowChangeSuggestedName() {
    return false;
  }

  @Override
  public @NlsContexts.DialogTitle String getDialogTitle() {
    return JavaRefactoringBundle.message("rename.accessors.title");
  }

  @Override
  public @NlsContexts.Button String getDialogDescription() {
    return JavaRefactoringBundle.message("rename.accessors.with.the.following.names.to");
  }

  @Override
  public String entityName() {
    return JavaRefactoringBundle.message("entity.name.accessor");
  }

  private static void prepareFieldRenaming(PsiField field, String newName, final Map<PsiElement, String> allRenames) {
    // search for getters/setters
    PsiClass aClass = field.getContainingClass();

    Project project = field.getProject();
    final JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(project);

    final String propertyName = PropertyUtilBase.suggestPropertyName(field, field.getName());
    final String newPropertyName = PropertyUtilBase.suggestPropertyName(field, newName);

    boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);

    PsiMethod[] getters = GetterSetterPrototypeProvider.findGetters(aClass, propertyName, isStatic);

    PsiMethod setter = PropertyUtilBase.findPropertySetter(aClass, propertyName, isStatic, false);

    boolean shouldRenameSetterParameter = false;

    if (setter != null) {
      shouldRenameSetterParameter = shouldRenameSetterParameter(manager, propertyName, setter);
    }

    if (getters != null) {
      List<PsiMethod> validGetters = new ArrayList<>();
      for (PsiMethod getter : getters) {
        String newGetterName = GetterSetterPrototypeProvider.suggestNewGetterName(propertyName, newPropertyName, getter);
        String getterId = null;
        if (newGetterName == null) {
          getterId = getter.getName();
          newGetterName = PropertyUtilBase.suggestGetterName(newPropertyName, field.getType(), getterId);
        }
        if (newGetterName.equals(getterId)) {
          continue;
        }
        else {
          boolean valid = true;
          for (PsiMethod method : getter.findDeepestSuperMethods()) {
            if (method instanceof PsiCompiledElement) {
              valid = false;
              break;
            }
          }
          if (!valid) continue;
        }
        validGetters.add(getter);
      }
      getters = validGetters.isEmpty() ? null : validGetters.toArray(PsiMethod.EMPTY_ARRAY);
    }

    String newSetterName = "";
    if (setter != null) {
      newSetterName = PropertyUtilBase.suggestSetterName(newPropertyName);
      final String newSetterParameterName = manager.propertyNameToVariableName(newPropertyName, VariableKind.PARAMETER);
      if (newSetterName.equals(setter.getName())) {
        setter = null;
        newSetterName = null;
        shouldRenameSetterParameter = false;
      }
      else if (newSetterParameterName.equals(setter.getParameterList().getParameters()[0].getName())) {
        shouldRenameSetterParameter = false;
      } else {
        for (PsiMethod method : setter.findDeepestSuperMethods()) {
          if (method instanceof PsiCompiledElement) {
            setter = null;
            shouldRenameSetterParameter = false;
            break;
          }
        }
      }
    }

    if (getters != null) {
      for (PsiMethod getter : getters) {
        String newGetterName = GetterSetterPrototypeProvider.suggestNewGetterName(propertyName, newPropertyName, getter);
        if (newGetterName == null) {
          newGetterName = PropertyUtilBase.suggestGetterName(newPropertyName, field.getType(), getter.getName());
        }
        addOverriddenAndImplemented(getter, newGetterName, null, propertyName, manager, allRenames);
      }
    }

    if (setter != null) {
      addOverriddenAndImplemented(setter, newSetterName, shouldRenameSetterParameter ? newPropertyName : null, propertyName, manager, allRenames);
    }
  }

  private static boolean shouldRenameSetterParameter(JavaCodeStyleManager manager, String propertyName, PsiMethod setter) {
    boolean shouldRenameSetterParameter;
    String parameterName = manager.propertyNameToVariableName(propertyName, VariableKind.PARAMETER);
    PsiParameter setterParameter = setter.getParameterList().getParameters()[0];
    shouldRenameSetterParameter = parameterName.equals(setterParameter.getName());
    return shouldRenameSetterParameter;
  }

  public static void addOverriddenAndImplemented(@NotNull final PsiMethod methodPrototype,
                                                   @NotNull final String newName,
                                                   @Nullable final String newPropertyName,
                                                   @NotNull final String oldParameterName,
                                                   @NotNull final JavaCodeStyleManager manager,
                                                   @NotNull final Map<PsiElement, String> allRenames) {
    PsiMethod[] methods = methodPrototype.findDeepestSuperMethods();
    if (methods.length == 0) {
      methods = new PsiMethod[] {methodPrototype};
    }
    for (PsiMethod method : methods) {
      addGetterOrSetterWithParameter(method, newName, newPropertyName, oldParameterName, manager, allRenames);
      OverridingMethodsSearch.search(method).forEach(psiMethod -> {
        RenameProcessor.assertNonCompileElement(psiMethod);
        addGetterOrSetterWithParameter(psiMethod, newName, newPropertyName, oldParameterName, manager, allRenames);
        return true;
      });
    }
  }

  private static void addGetterOrSetterWithParameter(@NotNull PsiMethod methodPrototype,
                                                       @NotNull String newName,
                                                       @Nullable String newPropertyName,
                                                       @NotNull String oldParameterName,
                                                       @NotNull JavaCodeStyleManager manager,
                                                       @NotNull Map<PsiElement, String> allRenames) {

    allRenames.put(methodPrototype, newName);
    if (newPropertyName != null) {
      final PsiParameter[] parameters = methodPrototype.getParameterList().getParameters();
      LOG.assertTrue(parameters.length > 0, methodPrototype.getName());
      PsiParameter parameter = parameters[0];
      if (shouldRenameSetterParameter(manager, oldParameterName , methodPrototype)) {
        allRenames.put(parameter, manager.propertyNameToVariableName(newPropertyName, VariableKind.PARAMETER));
      }
    }
  }
}
