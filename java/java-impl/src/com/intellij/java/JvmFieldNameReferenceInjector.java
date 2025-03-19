// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.injection.ReferenceInjector;
import com.intellij.util.ProcessingContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElementKt;
import org.jetbrains.uast.UField;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.expressions.UInjectionHost;

import javax.swing.*;

public final class JvmFieldNameReferenceInjector extends ReferenceInjector {
  @Override
  public @NotNull String getId() {
    return "jvm-field-name";
  }

  @Override
  public @NotNull @NlsContexts.Label String getDisplayName() {
    return JavaBundle.message("label.jvm.field.name");
  }

  @Override
  public PsiReference @NotNull [] getReferences(@NotNull PsiElement element,
                                                @NotNull ProcessingContext context,
                                                @NotNull TextRange range) {
    UInjectionHost host = UastContextKt.toUElement(element, UInjectionHost.class);
    if (host == null) return PsiReference.EMPTY_ARRAY;
    String text = range.substring(element.getText());
    if (text.isEmpty()) return PsiReference.EMPTY_ARRAY;
    UClass uClass = UastContextKt.getUastParentOfType(element, UClass.class);
    if (uClass == null) return PsiReference.EMPTY_ARRAY;
    PsiClass containingClass = UElementKt.getAsJavaPsiElement(uClass, PsiClass.class);
    if (containingClass == null) return PsiReference.EMPTY_ARRAY;
    PsiField field = containingClass.findFieldByName(text, true);
    if (field != null && !field.hasModifierProperty(PsiModifier.STATIC)) {
      return new JavaFieldReference[]{new JavaFieldReference(element, range, field, uClass)};
    }
    return new JavaFieldReference[]{new JavaFieldReference(element, range, null, uClass)};
  }

  @Override
  public @NotNull Icon getIcon() {
    return AllIcons.Nodes.Field;
  }

  private static final class JavaFieldReference extends PsiReferenceBase<PsiElement> {
    private final @Nullable PsiField myField;
    private final @NotNull UClass myContainingClass;

    JavaFieldReference(@NotNull PsiElement literal, @NotNull TextRange range,
                        @Nullable PsiField field, @NotNull UClass containingClass) {
      super(literal, range, field == null);
      myField = field;
      myContainingClass = containingClass;
    }

    @Override
    public Object @NotNull [] getVariants() {
      return StreamEx.of(myContainingClass.getJavaPsi().getAllFields())
        .remove(field -> field.hasModifierProperty(PsiModifier.STATIC))
        .distinct(PsiField::getName).toArray(PsiField.EMPTY_ARRAY);
    }

    @Override
    public @Nullable PsiElement resolve() {
      UField uField = UastContextKt.toUElement(myField, UField.class);
      return uField == null ? null : uField.getSourcePsi();
    }
  }
}
