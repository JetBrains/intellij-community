// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.UpdateInspectionOptionFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class SortedCollectionWithNonComparableKeysInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Set<String> COLLECTIONS = Set.of(
    "java.util.TreeSet", "java.util.TreeMap", "java.util.concurrent.ConcurrentSkipListSet", "java.util.concurrent.ConcurrentSkipListMap");

  public boolean IGNORE_TYPE_PARAMETERS;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("IGNORE_TYPE_PARAMETERS",
               JavaBundle.message("inspection.sorted.collection.with.non.comparable.keys.option.type.parameters")));
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitNewExpression(@NotNull PsiNewExpression expression) {
        if (expression.getAnonymousClass() != null || expression.isArrayCreation() ||
            expression.getArgumentList() == null || !expression.getArgumentList().isEmpty()) {
          return;
        }
        PsiJavaCodeReferenceElement reference = expression.getClassReference();
        if (reference == null) return;
        String qualifiedName = reference.getQualifiedName();
        if (!COLLECTIONS.contains(qualifiedName)) return;
        PsiClassType type = ObjectUtils.tryCast(expression.getType(), PsiClassType.class);
        if (type == null || type.isRaw()) return;
        PsiType elementType = ArrayUtil.getFirstElement(type.getParameters());
        if (elementType == null || TypeUtils.isJavaLangObject(elementType)) return;
        ModCommandAction fix = null;
        if (elementType instanceof PsiClassType && ((PsiClassType)elementType).resolve() instanceof PsiTypeParameter) {
          if (IGNORE_TYPE_PARAMETERS) return;
          String message = JavaBundle.message("inspection.sorted.collection.with.non.comparable.keys.option.type.parameters");
          fix = new UpdateInspectionOptionFix(SortedCollectionWithNonComparableKeysInspection.this, "IGNORE_TYPE_PARAMETERS", message, true);
        }
        if (InheritanceUtil.isInheritor(elementType, CommonClassNames.JAVA_LANG_COMPARABLE)) return;
        holder.problem(expression, JavaBundle.message("inspection.sorted.collection.with.non.comparable.keys.message"))
          .maybeFix(fix).register();
      }
    };
  }
}
