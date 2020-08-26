// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class GuavaOptionalConversionRule extends BaseGuavaTypeConversionRule {
  private final static Logger LOG = Logger.getInstance(GuavaOptionalConversionRule.class);

  public final static @NonNls String OPTIONAL_CONVERTOR_PATTERN = "Optional.fromNullable($o$.orElse(null))";
  public final static @NonNls String GUAVA_OPTIONAL = "com.google.common.base.Optional";
  public final static @NonNls String JAVA_OPTIONAL = "java.util.Optional";

  @Nullable
  @Override
  protected TypeConversionDescriptorBase findConversionForMethod(@Nullable PsiType from,
                                                                 @Nullable PsiType to,
                                                                 @NotNull PsiMethod method,
                                                                 @NotNull String methodName,
                                                                 PsiExpression context,
                                                                 TypeMigrationLabeler labeler) {
    if (!(context instanceof PsiMethodCallExpression)) {
      if ("or".equals(methodName)) {
        PsiMethodCallExpression methodCallExpression = null;
        if (context.getParent() instanceof PsiMethodCallExpression) {
          methodCallExpression = (PsiMethodCallExpression)context.getParent();
        }
        if (methodCallExpression == null) {
          return null;
        }
        final PsiClass aClass = getParameterClass(method);
        if (aClass != null) {
          final String qName = aClass.getQualifiedName();
          if (GUAVA_OPTIONAL.equals(qName)) {
            TypeConversionDescriptor descriptor =
              new TypeConversionDescriptor(null, "java.util.Optional.ofNullable($val$.orElseGet($o$::get))") {
                @Override
                public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) {
                  setStringToReplace("$val$.or(" +
                                     GuavaOptionalConversionUtil.simplifyParameterPattern((PsiMethodCallExpression)expression)
                                     + ")");
                  return super.replace(expression, evaluator);
                }
              };
            if (to != null) {
              descriptor.withConversionType(to);
            }
            return descriptor;
          }
          return GuavaLambda.SUPPLIER.getClassQName().equals(qName)
                 ? new GuavaTypeConversionDescriptor("$val$.or($other$)", "$val$.orElseGet($other$)", context)
                 : new TypeConversionDescriptor("$val$.or($other$)", "$val$.orElse($other$)");
        }
        return null;
      }
      else if ("transform".equals(methodName)) {
        final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)(context.getParent());
        final PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
        if (arguments.length != 1) {
          return null;
        }
        final PsiExpression functionArgument = arguments[0];
        final TypeConversionDescriptor descriptor = new GuavaTypeConversionDescriptor("$val$.transform($fun$)", "$val$.map($fun$)", context);
        final PsiType typeParameter = GuavaConversionUtil.getFunctionReturnType(functionArgument);
        if (typeParameter == null) {
          return descriptor;
        }
        final String rawOptionalType = JAVA_OPTIONAL + "<" + typeParameter.getCanonicalText(false) + ">";
        return descriptor.withConversionType(JavaPsiFacade.getElementFactory(method.getProject()).createTypeFromText(rawOptionalType, context));
      }
      return null;
    }
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null || !(GuavaFluentIterableConversionRule.FLUENT_ITERABLE.equals(aClass.getQualifiedName()) ||
                            GUAVA_OPTIONAL.equals(aClass.getQualifiedName()))) {
      return null;
    }
    return GuavaFluentIterableConversionRule.buildCompoundDescriptor((PsiMethodCallExpression) context, to, labeler);
  }

  @Override
  protected boolean isValidMethodQualifierToConvert(PsiClass aClass) {
    return super.isValidMethodQualifierToConvert(aClass) ||
           (aClass != null && GuavaFluentIterableConversionRule.FLUENT_ITERABLE.equals(aClass.getQualifiedName()));
  }

  @Nullable
  @Override
  protected TypeConversionDescriptorBase findConversionForVariableReference(@Nullable PsiExpression context) {
    if (GuavaOptionalConversionUtil.isOptionalOrContext(context)) {
      return new TypeConversionDescriptor("$o$", "com.google.common.base." + OPTIONAL_CONVERTOR_PATTERN);
    }
    return new TypeConversionDescriptor("$o$", "$o$::get");
  }

  private static PsiClass getParameterClass(PsiMethod method) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) {
      return null;
    }
    return PsiTypesUtil.getPsiClass(parameters[0].getType());
  }

  @Override
  protected void fillSimpleDescriptors(Map<@NonNls String, TypeConversionDescriptorBase> descriptorsMap) {
    descriptorsMap.put("absent", new TypeConversionDescriptor("'_Optional?.absent()", "java.util.Optional.empty()") {
      @Override
      public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) {
        LOG.assertTrue(expression instanceof PsiMethodCallExpression);

        final PsiReferenceParameterList typeArguments = ((PsiMethodCallExpression)expression).getTypeArgumentList();
        PsiReferenceParameterList typeArgumentsCopy =
          typeArguments.getTypeArguments().length == 0 ? null : (PsiReferenceParameterList)typeArguments.copy();
        final PsiMethodCallExpression replacedExpression = (PsiMethodCallExpression)super.replace(expression, evaluator);
        if (typeArgumentsCopy != null) {
          replacedExpression.getTypeArgumentList().replace(typeArgumentsCopy);
        }
        return replacedExpression;
      }

    });

    descriptorsMap.put("of", new TypeConversionDescriptor("'_Optional?.of($ref$)", "java.util.Optional.of($ref$)"));
    descriptorsMap.put("fromNullable", new TypeConversionDescriptor("'_Optional?.fromNullable($ref$)", "java.util.Optional.ofNullable($ref$)"));
    descriptorsMap.put("presentInstances", new TypeConversionDescriptor("'_Optional?.presentInstances($it$)", "java.util.stream.StreamSupport.stream($it$.spliterator(), false).map(java.util.Optional::get).collect(java.util.Collectors.toList())"));

    final TypeConversionDescriptorBase identity = new TypeConversionDescriptorBase();
    descriptorsMap.put("get", identity);
    descriptorsMap.put("isPresent", identity);
    descriptorsMap.put("orNull", new TypeConversionDescriptor("$val$.orNull()", "$val$.orElse(null)"));
    descriptorsMap.put("asSet", new TypeConversionDescriptor("$val$.asSet()",
                                                             "$val$.map(java.util.Collections::singleton).orElse(java.util.Collections.emptySet())"));
  }

  @NotNull
  @Override
  public String ruleFromClass() {
    return GUAVA_OPTIONAL;
  }

  @NotNull
  @Override
  public String ruleToClass() {
    return JAVA_OPTIONAL;
  }

  @Override
  protected TypeConversionDescriptorBase getUnknownMethodConversion() {
    return null;
  }
}
