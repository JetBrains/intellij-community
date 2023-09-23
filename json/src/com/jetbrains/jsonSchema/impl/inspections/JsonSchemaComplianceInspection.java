// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.json.JsonBundle;
import com.intellij.json.psi.JsonElementVisitor;
import com.intellij.json.psi.JsonValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.impl.JsonSchemaComplianceChecker;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class JsonSchemaComplianceInspection extends JsonSchemaBasedInspectionBase {
  public boolean myCaseInsensitiveEnum = false;

  @Override
  protected PsiElementVisitor doBuildVisitor(@NotNull JsonValue root, @Nullable JsonSchemaObject schema, @NotNull JsonSchemaService service,
                                             @NotNull ProblemsHolder holder,
                                             @NotNull LocalInspectionToolSession session) {
    if (schema == null) return PsiElementVisitor.EMPTY_VISITOR;
    JsonComplianceCheckerOptions options = new JsonComplianceCheckerOptions(myCaseInsensitiveEnum);

    return new JsonElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element == root) {
          // perform this only for the root element, because the checker traverses the hierarchy itself
          annotate(element, schema, holder, session, options);
        }
        super.visitElement(element);
      }
    };
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("myCaseInsensitiveEnum", JsonBundle.message("json.schema.inspection.case.insensitive.enum")));
  }

  private static void annotate(@NotNull PsiElement element,
                               @NotNull JsonSchemaObject rootSchema,
                               @NotNull ProblemsHolder holder,
                               @NotNull LocalInspectionToolSession session,
                               JsonComplianceCheckerOptions options) {
    final JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element, rootSchema);
    if (walker == null) return;
    new JsonSchemaComplianceChecker(rootSchema, holder, walker, session, options).annotate(element);
  }
}
