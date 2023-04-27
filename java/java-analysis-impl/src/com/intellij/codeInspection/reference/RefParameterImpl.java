// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class RefParameterImpl extends RefJavaElementImpl implements RefParameter {
  private static final int USED_FOR_READING_MASK = 0b1_00000000_00000000;
  private static final int USED_FOR_WRITING_MASK = 0b10_00000000_00000000;

  private final short myIndex;
  private Object myActualValueTemplate; // guarded by this
  private short myUsageCount; // guarded by this

  RefParameterImpl(UParameter parameter, PsiElement psi, int index, RefManager manager, RefElement refElement) {
    super(parameter, psi, manager);

    myIndex = (short)index;
    myActualValueTemplate = VALUE_UNDEFINED;
    final RefElementImpl owner = (RefElementImpl)refElement;
    if (owner != null) {
      owner.add(this);
    }

    if (psi.getLanguage().isKindOf("kotlin")) {
      //TODO kotlin receiver parameter must be used
      if (myIndex == 0) {
        String name = getName();
        if ("$receiver".equals(name) || name.startsWith("$this$")) {
          setUsedForReading();
        }
      }
    }
  }

  @Override
  public void parameterReferenced(boolean forWriting) {
    if (forWriting) {
      setUsedForWriting();
    }
    else {
      setUsedForReading();
    }
  }

  @Override
  public boolean isUsedForReading() {
    return checkFlag(USED_FOR_READING_MASK);
  }

  void setUsedForReading() {
    setFlag(true, USED_FOR_READING_MASK);
  }

  @Override
  public synchronized int getUsageCount() {
    return myUsageCount;
  }

  @Override
  public boolean isUsedForWriting() {
    return checkFlag(USED_FOR_WRITING_MASK);
  }

  private void setUsedForWriting() {
    setFlag(true, USED_FOR_WRITING_MASK);
  }

  @Override
  public void accept(@NotNull final RefVisitor visitor) {
    if (visitor instanceof RefJavaVisitor) {
      ApplicationManager.getApplication().runReadAction(() -> ((RefJavaVisitor)visitor).visitParameter(this));
    }
    else {
      super.accept(visitor);
    }
  }

  @Override
  public int getIndex() {
    return myIndex;
  }

  @Override
  public void buildReferences() {
    final RefJavaUtil refUtil = RefJavaUtil.getInstance();
    final UParameter parameter = getUastElement();
    if (parameter != null) {
      List<UAnnotation> annotations = parameter.getUAnnotations();
      refUtil.addReferencesTo(parameter, this, annotations.toArray(UElementKt.EMPTY_ARRAY));
      UTypeReferenceExpression typeReference = parameter.getTypeReference();
      refUtil.addReferencesTo(parameter, this, typeReference);
    }
  }

  synchronized void clearTemplateValue() {
    myActualValueTemplate = VALUE_IS_NOT_CONST;
  }

  synchronized void updateTemplateValue(UExpression expression, @Nullable PsiElement accessPlace) {
    myUsageCount++;
    if (myActualValueTemplate == VALUE_IS_NOT_CONST) return;

    Object newTemplate = getAccessibleExpressionValue(expression, () -> accessPlace == null ? getContainingFile() : accessPlace);
    if (myActualValueTemplate == VALUE_UNDEFINED) {
      myActualValueTemplate = newTemplate;
    }
    else if (!Comparing.equal(myActualValueTemplate, newTemplate)) {
      myActualValueTemplate = VALUE_IS_NOT_CONST;
    }
  }

  @Nullable
  @Override
  public synchronized Object getActualConstValue() {
    return myActualValueTemplate;
  }

  @Override
  protected void initialize() {
  }

  @Override
  public String getExternalName() {
    return ReadAction.compute(() -> {
      UParameter parameter = getUastElement();
      LOG.assertTrue(parameter != null);
      return PsiFormatUtil.getExternalName((PsiModifierListOwner)parameter.getJavaPsi());
    });
  }

  @Override
  public UParameter getUastElement() {
    // kotlin receiver parameter (psi <-> uast conversion isn't symmetric)
    RefMethod method = ObjectUtils.tryCast(getOwner(), RefMethod.class);
    if (method == null) return null;
    UMethod uMethod = ObjectUtils.tryCast(method.getUastElement(), UMethod.class);
    if (uMethod == null) return null;
    List<UParameter> parameters = uMethod.getUastParameters();
    if (parameters.size() <= getIndex()) return null;
    return parameters.get(getIndex());
  }

  @Nullable
  public static Object getAccessibleExpressionValue(@Nullable UExpression expression, @NotNull Supplier<? extends PsiElement> accessPlace) {
    if (expression == null) return null;
    if (expression instanceof UExpressionList expressionList) {
      List<Object> exprValues = ContainerUtil.map(expressionList.getExpressions(), expr -> getAccessibleExpressionValue(expr, accessPlace));
      return ContainerUtil.all(exprValues, value -> value == VALUE_IS_NOT_CONST) ? VALUE_IS_NOT_CONST : exprValues;
    }
    if (expression instanceof UReferenceExpression referenceExpression) {
      UElement resolved = UResolvableKt.resolveToUElement(referenceExpression);
      if (resolved instanceof UField uField) {
        PsiElement element = accessPlace.get();
        if (uField.isStatic() && uField.isFinal()) {
          if (element == null || !isAccessible(uField, element)) {
            return VALUE_IS_NOT_CONST;
          }
          UDeclaration containingClass = UDeclarationKt.getContainingDeclaration(uField);
          if (containingClass instanceof UClass && ((UClass)containingClass).getQualifiedName() != null) {
            return uField;
          }
        }
      }
    }
    if (expression instanceof ULiteralExpression) {
      Object value = ((ULiteralExpression)expression).getValue();
      if (value == null) {
        return null;
      }
      //don't unescape/escape to insert into the source file
      PsiElement sourcePsi = Objects.requireNonNull(expression.getSourcePsi());
      return value instanceof String ? ("\"" + StringUtil.unquoteString(sourcePsi.getText()) + "\"") : value;
    }
    Object constValue = expression.evaluate(); //JavaConstantExpressionEvaluator.computeConstantExpression(expression, false);
    return constValue == null ? VALUE_IS_NOT_CONST : constValue instanceof String ? "\"" + constValue + "\"" : constValue;
  }

  private static boolean isAccessible(@NotNull UField field, @NotNull PsiElement place) {
    UDeclaration fieldContainingClass = UDeclarationKt.getContainingDeclaration(field);
    if (!(fieldContainingClass instanceof UClass)) return false;
    String qName = ((UClass)fieldContainingClass).getQualifiedName();
    if (qName == null) return false;
    String fieldQName = qName + "." + field.getName();
    return PsiResolveHelper.getInstance(place.getProject()).resolveReferencedVariable(fieldQName, place) != null;
  }

  @Nullable
  static RefElement parameterFromExternalName(final RefManager manager, final String fqName) {
    final int idx = fqName.lastIndexOf(' ');
    if (idx > 0) {
      final String method = fqName.substring(0, idx);
      final RefJavaElement refMethod = RefMethodImpl.methodFromExternalName(manager, method);
      if (refMethod != null) {
        final UMethod element = ObjectUtils.tryCast(refMethod.getUastElement(), UMethod.class);
        if (element == null) return null;
        final List<UParameter> parameters = element.getUastParameters();
        int paramIdx = 0;
        final String paramName = fqName.substring(idx + 1);
        for (UParameter parameter : parameters) {
          final String name = parameter.getName();
          if (name != null && name.equals(paramName)) {
            return manager.getExtension(RefJavaManager.MANAGER).getParameterReference(parameter, paramIdx, refMethod);
          }
          paramIdx++;
        }
      }
    }
    return null;
  }
}
