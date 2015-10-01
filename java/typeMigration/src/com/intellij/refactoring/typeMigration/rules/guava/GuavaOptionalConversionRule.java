/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class GuavaOptionalConversionRule extends BaseGuavaTypeConversionRule {
  private final static Logger LOG = Logger.getInstance(GuavaOptionalConversionRule.class);

  public final static String GUAVA_OPTIONAL = "com.google.common.base.Optional";

  @Nullable
  @Override
  protected TypeConversionDescriptorBase findConversionForMethod(@NotNull PsiType from,
                                                                 @NotNull PsiType to,
                                                                 @NotNull PsiMethod method,
                                                                 String methodName,
                                                                 PsiExpression context,
                                                                 TypeMigrationLabeler labeler) {
    if ("or".equals(methodName)) {
      PsiMethodCallExpression methodCallExpression = null;
      if (context instanceof PsiMethodCallExpression) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length != 1) {
          return null;
        }
        final PsiClass aClass = PsiTypesUtil.getPsiClass(parameters[0].getType());
        if (aClass != null) {
          final String qName = aClass.getQualifiedName();
          String pattern =
            GUAVA_OPTIONAL.equals(qName) ? "java.util.Optional.ofNullable($expr$.get())" : "com.google.common.bas.Supplier".equals(qName)
                                                                                           ? "java.util.Optional.ofNullable($expr$)"
                                                                                           : "java.util.Optional.ofNullable($expr$)";
          return new TypeConversionDescriptor("$expr$", pattern);
        }
        return null;
      } else if (context.getParent() instanceof PsiMethodCallExpression) {
        methodCallExpression = (PsiMethodCallExpression)context.getParent();
      }
      if (methodCallExpression == null) {
        return null;
      }
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length != 1) {
        return null;
      }
      final PsiClass aClass = PsiTypesUtil.getPsiClass(parameters[0].getType());
      if (aClass != null) {
        final String qName = aClass.getQualifiedName();
        if (GUAVA_OPTIONAL.equals(qName)) {
          return new TypeConversionDescriptor("$val$.or($other$)", "java.util.Optional.ofNullable($val$.orElseGet($other$::get))", to);
        }
        String pattern = "com.google.common.bas.Supplier".equals(qName) ? "$val$.orElseGet($other$::get)" : "$val$.orElse($other$)";
        return new TypeConversionDescriptor("$val$.or($other$)", pattern);
      }
      return null;
    }
    return null;
  }

  @Override
  protected void fillSimpleDescriptors(Map<String, TypeConversionDescriptorBase> descriptorsMap) {
    descriptorsMap.put("absent", new TypeConversionDescriptor("Optional.absent()", "java.util.Optional.empty()") {
      @Override
      public PsiExpression replace(PsiExpression expression) {
        LOG.assertTrue(expression instanceof PsiMethodCallExpression);

        final PsiReferenceParameterList typeArguments = ((PsiMethodCallExpression)expression).getTypeArgumentList();
        PsiReferenceParameterList typeArgumentsCopy =
          typeArguments.getTypeArguments().length == 0 ? null : (PsiReferenceParameterList)typeArguments.copy();
        final PsiMethodCallExpression replacedExpression = (PsiMethodCallExpression)super.replace(expression);
        if (typeArgumentsCopy != null) {
          replacedExpression.getTypeArgumentList().replace(typeArgumentsCopy);
        }
        return replacedExpression;
      }

    });

    descriptorsMap.put("of", new TypeConversionDescriptor("Optional.of($ref$)", "java.util.Optional.of($ref$)"));
    descriptorsMap.put("fromNullable", new TypeConversionDescriptor("Optional.fromNullable($ref$)", "java.util.Optional.ofNullable($ref$)"));
    descriptorsMap.put("presentInstances", new TypeConversionDescriptor("Optional.presentInstances($it$)", "java.util.stream.StreamSupport.stream($it$.spliterator(), false).map(java.util.Optional::get).collect(java.util.Collectors.toList())"));

    final TypeConversionDescriptorBase identity = new TypeConversionDescriptorBase();
    descriptorsMap.put("get", identity);
    descriptorsMap.put("isPresent", identity);
    descriptorsMap.put("orNull", new TypeConversionDescriptor("$val$.orNull()", "$val$.orElse(null)"));
    descriptorsMap.put("asSet", new TypeConversionDescriptor("$val$.asSet()",
                                                             "$val$.isPresent() ? java.util.Collections.singleton($val$.get()) : java.util.Collections.emptySet()"));
    descriptorsMap.put("transform", new TypeConversionDescriptor("$val$.transform($fun$)", "$val$.map($fun$)"));
  }

  @NotNull
  @Override
  public String ruleFromClass() {
    return "com.google.common.base.Optional";
  }

  @NotNull
  @Override
  public String ruleToClass() {
    return "java.util.Optional";
  }
}
