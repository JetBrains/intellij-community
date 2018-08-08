// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.json.JsonBundle;
import com.intellij.json.psi.JsonElementVisitor;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JsonSchemaComplianceInspection extends LocalInspectionTool {
  public boolean myCaseInsensitiveEnum = false;

  @NotNull
  public String getDisplayName() {
    return JsonBundle.message("json.schema.inspection.compliance.name");
  }

  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    PsiFile file = holder.getFile();
    JsonValue root = file instanceof JsonFile ? ObjectUtils.tryCast(file.getFirstChild(), JsonValue.class) : null;
    if (root == null) return PsiElementVisitor.EMPTY_VISITOR;

    JsonSchemaService service = JsonSchemaService.Impl.get(file.getProject());
    VirtualFile virtualFile = file.getViewProvider().getVirtualFile();
    if (!service.isApplicableToFile(virtualFile)) return PsiElementVisitor.EMPTY_VISITOR;
    final JsonSchemaObject rootSchema = service.getSchemaObject(virtualFile);
    if (rootSchema == null) return PsiElementVisitor.EMPTY_VISITOR;
    JsonComplianceCheckerOptions options = new JsonComplianceCheckerOptions(myCaseInsensitiveEnum);

    return new JsonElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element == root) {
          // perform this only for the root element, because the checker traverses the hierarchy itself
          annotate(element, rootSchema, holder, session, options);
        }
        super.visitElement(element);
      }
    };
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(JsonBundle.message("json.schema.inspection.case.insensitive.enum"), "myCaseInsensitiveEnum");
    return optionsPanel;
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
