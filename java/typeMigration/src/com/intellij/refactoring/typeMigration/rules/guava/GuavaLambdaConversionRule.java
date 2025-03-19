// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.refactoring.typeMigration.inspections.GuavaConversionSettings;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class GuavaLambdaConversionRule extends BaseGuavaTypeConversionRule {
  private final GuavaLambda myLambda;

  protected GuavaLambdaConversionRule(GuavaLambda lambda) {
    myLambda = lambda;
  }

  @Override
  protected void fillSimpleDescriptors(Map<String, TypeConversionDescriptorBase> descriptorsMap) {
    descriptorsMap.put(myLambda.getSamName(), new FunctionalInterfaceTypeConversionDescriptor(myLambda.getSamName(), myLambda.getJavaAnalogueSamName(), myLambda.getJavaAnalogueClassQName()));
  }

  @Override
  protected @Nullable TypeConversionDescriptorBase findConversionForVariableReference(PsiExpression context) {
    return new FunctionalInterfaceTypeConversionDescriptor(myLambda.getSamName(), myLambda.getJavaAnalogueSamName(), myLambda.getJavaAnalogueClassQName());
  }

  @Override
  public @NotNull String ruleFromClass() {
    return myLambda.getClassQName();
  }

  @Override
  public @NotNull String ruleToClass() {
    return myLambda.getJavaAnalogueClassQName();
  }

  @Override
  protected TypeConversionDescriptorBase getUnknownMethodConversion() {
    return new TypeConversionDescriptor("$q$.$m$($args$)", "$q$.$m$($args$)::" + myLambda.getJavaAnalogueSamName());
  }

  @Override
  protected @Nullable TypeConversionDescriptorBase findConversionForAnonymous(@NotNull PsiAnonymousClass anonymousClass,
                                                                              GuavaConversionSettings settings) {
    final TypeConversionDescriptorBase conversion = super.findConversionForAnonymous(anonymousClass, settings);
    if (conversion != null) {
      return conversion;
    }
    final PsiClass baseClass = anonymousClass.getBaseClassType().resolve();
    return baseClass != null && myLambda.getClassQName().equals(baseClass.getQualifiedName())
           ? new ConvertLambdaClassToJavaClassDescriptor()
           : null;
  }

  public static final class Function extends GuavaLambdaConversionRule {
    public Function() {
      super(GuavaLambda.FUNCTION);
    }
  }

  public static final class Supplier extends GuavaLambdaConversionRule {
    public Supplier() {
      super(GuavaLambda.SUPPLIER);
    }
  }

  private static class ConvertLambdaClassToJavaClassDescriptor extends TypeConversionDescriptorBase {
    @Override
    public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) throws IncorrectOperationException {
      return GuavaConversionUtil.tryConvertClassAndSamNameToJava((PsiNewExpression)expression);
    }
  }
}

