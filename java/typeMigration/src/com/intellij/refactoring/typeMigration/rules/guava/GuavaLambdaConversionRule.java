/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

  @Nullable
  @Override
  protected TypeConversionDescriptorBase findConversionForVariableReference(PsiExpression context) {
    return new FunctionalInterfaceTypeConversionDescriptor(myLambda.getSamName(), myLambda.getJavaAnalogueSamName(), myLambda.getJavaAnalogueClassQName());
  }

  @NotNull
  @Override
  public String ruleFromClass() {
    return myLambda.getClassQName();
  }

  @NotNull
  @Override
  public String ruleToClass() {
    return myLambda.getJavaAnalogueClassQName();
  }

  @Override
  protected TypeConversionDescriptorBase getUnknownMethodConversion() {
    return new TypeConversionDescriptor("$q$.$m$($args$)", "$q$.$m$($args$)::" + myLambda.getJavaAnalogueSamName());
  }

  @Nullable
  @Override
  protected TypeConversionDescriptorBase findConversionForAnonymous(@NotNull PsiAnonymousClass anonymousClass,
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

  public static class Function extends GuavaLambdaConversionRule {
    public Function() {
      super(GuavaLambda.FUNCTION);
    }
  }

  public static class Supplier extends GuavaLambdaConversionRule {
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

