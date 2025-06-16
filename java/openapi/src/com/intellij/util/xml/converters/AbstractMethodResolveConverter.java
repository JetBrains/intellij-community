// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.converters;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.core.JavaPsiBundle;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

public abstract class AbstractMethodResolveConverter<ParentType extends DomElement> extends ResolvingConverter<PsiMethod> {
  public static final String ALL_METHODS = "*";
  private final Class<? extends ParentType> myDomMethodClass;

  protected AbstractMethodResolveConverter(final Class<? extends ParentType> domMethodClass) {
    myDomMethodClass = domMethodClass;
  }

  protected abstract @NotNull @Unmodifiable Collection<PsiClass> getPsiClasses(final ParentType parent, final ConvertContext context);

  protected abstract @Nullable AbstractMethodParams getMethodParams(@NotNull ParentType parent);

  @Override
  public void bindReference(final GenericDomValue<PsiMethod> genericValue, final ConvertContext context, final PsiElement element) {
    assert element instanceof PsiMethod : "PsiMethod expected";
    final PsiMethod psiMethod = (PsiMethod)element;
    final ParentType parent = getParent(context);
    final AbstractMethodParams methodParams = getMethodParams(parent);
    genericValue.setStringValue(psiMethod.getName());
    if (methodParams != null) {
      methodParams.undefine();
      for (PsiParameter parameter : psiMethod.getParameterList().getParameters()) {
        methodParams.addMethodParam().setValue(parameter.getType());
      }
    }
  }

  @Override
  public String getErrorMessage(final String s, final @NotNull ConvertContext context) {
    final ParentType parent = getParent(context);
    return CodeInsightBundle
      .message("error.cannot.resolve.0.1", JavaPsiBundle.message("element.method"), getReferenceCanonicalText(s, getMethodParams(parent)));
  }

  protected final @NotNull ParentType getParent(final ConvertContext context) {
    ParentType parent = context.getInvocationElement().getParentOfType(myDomMethodClass, true);
    assert parent != null: "Can't get parent of type " + myDomMethodClass + " for " + context.getInvocationElement();
    return parent;
  }

  @Override
  public boolean isReferenceTo(final @NotNull PsiElement element, final String stringValue, final PsiMethod resolveResult,
                               final @NotNull ConvertContext context) {
    if (super.isReferenceTo(element, stringValue, resolveResult, context)) return true;

    final Ref<Boolean> result = new Ref<>(Boolean.FALSE);
    processMethods(context, method -> {
      if (method.equals(element)) {
        result.set(Boolean.TRUE);
        return false;
      }
      return true;
    }, s -> s.findMethodsByName(stringValue, true));

    return result.get();
  }

  @SuppressWarnings({"WeakerAccess"})
  protected void processMethods(final ConvertContext context, Processor<? super PsiMethod> processor, Function<? super PsiClass, PsiMethod[]> methodGetter) {
    for (PsiClass psiClass : getPsiClasses(getParent(context), context)) {
      if (psiClass != null) {
        for (PsiMethod psiMethod : methodGetter.fun(psiClass)) {
          if (!processor.process(psiMethod)) {
            return;
          }
        }
      }
    }
  }

  @Override
  public @NotNull Collection<? extends PsiMethod> getVariants(final @NotNull ConvertContext context) {
    Set<PsiMethod> methodList = new LinkedHashSet<>();
    Processor<PsiMethod> processor = CommonProcessors.notNullProcessor(Processors.cancelableCollectProcessor(methodList));
    processMethods(context, processor, s -> {
      final List<PsiMethod> list = ContainerUtil.findAll(getVariants(s), object -> acceptMethod(object, context));
      return list.toArray(PsiMethod.EMPTY_ARRAY);
    });
    return methodList;
  }

  protected @Unmodifiable Collection<PsiMethod> getVariants(final PsiClass s) {
    return Arrays.asList(s.getAllMethods());
  }

  protected boolean acceptMethod(final PsiMethod psiMethod, final ConvertContext context) {
    return methodSuits(psiMethod);
  }

  public static boolean methodSuits(final PsiMethod psiMethod) {
    if (psiMethod.isConstructor()) return false;
    return psiMethod.getContainingClass().isInterface() || (!psiMethod.hasModifierProperty(PsiModifier.FINAL) && !psiMethod.hasModifierProperty(PsiModifier.STATIC));
  }

  @Override
  public @NotNull @Unmodifiable Set<String> getAdditionalVariants(@NotNull ConvertContext context) {
    return Collections.singleton(ALL_METHODS);
  }

  @Override
  public PsiMethod fromString(final String methodName, final @NotNull ConvertContext context) {
    final CommonProcessors.FindFirstProcessor<PsiMethod> processor = new CommonProcessors.FindFirstProcessor<>();
    processMethods(context, processor, s -> {
      final PsiMethod method = findMethod(s, methodName, getMethodParams(getParent(context)));
      if (method != null && acceptMethod(method, context)) {
        return new PsiMethod[]{method};
      }
      return PsiMethod.EMPTY_ARRAY;
    });
    if (processor.isFound()) return processor.getFoundValue();

    processMethods(context, processor, s -> s.findMethodsByName(methodName, true));
    return processor.getFoundValue();
  }

  @Override
  public String toString(final PsiMethod method, final @NotNull ConvertContext context) {
    return method.getName();
  }

  public static String getReferenceCanonicalText(final String name, final @Nullable AbstractMethodParams methodParams) {
    StringBuilder sb = new StringBuilder(name);
    if (methodParams == null) {
      sb.append("()");
    }
    else if (methodParams.getXmlTag() != null) {
      sb.append("(");
      final List<GenericDomValue<PsiType>> list = methodParams.getMethodParams();
      boolean first = true;
      for (GenericDomValue<PsiType> value : list) {
        if (first) first = false;
        else sb.append(", ");
        sb.append(value.getStringValue());
      }
      sb.append(")");
    }
    return sb.toString();
  }

  public static @Nullable PsiMethod findMethod(final PsiClass psiClass, final String methodName, final @Nullable AbstractMethodParams methodParameters) {
    if (psiClass == null || methodName == null) return null;
    return ContainerUtil.find(psiClass.findMethodsByName(methodName, true), object -> methodParamsMatchSignature(methodParameters, object));
  }

  public static boolean methodParamsMatchSignature(final @Nullable AbstractMethodParams params, final PsiMethod psiMethod) {
    if (params != null && params.getXmlTag() == null) return true;

    PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
    if (params == null) return parameters.length == 0;

    List<GenericDomValue<PsiType>> methodParams = params.getMethodParams();
    if (methodParams.size() != parameters.length) return false;
    for (int i = 0; i < parameters.length; i++) {
      if (!Comparing.equal(parameters[i].getType(), methodParams.get(i).getValue())) {
        return false;
      }
    }
    return true;
  }

}
