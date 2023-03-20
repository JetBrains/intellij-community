// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonOriginalPsiWalker;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class JsonSchemaBasedInspectionBase extends LocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    PsiFile file = holder.getFile();
    Collection<PsiElement> allRoots = JsonOriginalPsiWalker.INSTANCE.getRoots(file);
    // JSON may have only a single root element
    JsonValue root = allRoots.size() == 1 ? ObjectUtils.tryCast(ContainerUtil.getFirstItem(allRoots), JsonValue.class) : null;
    if (root == null) return PsiElementVisitor.EMPTY_VISITOR;

    JsonSchemaService service = JsonSchemaService.Impl.get(file.getProject());
    VirtualFile virtualFile = file.getViewProvider().getVirtualFile();
    if (!service.isApplicableToFile(virtualFile)) return PsiElementVisitor.EMPTY_VISITOR;

    return doBuildVisitor(root, service.getSchemaObject(file), service, holder, session);
  }

  protected abstract PsiElementVisitor doBuildVisitor(@NotNull JsonValue root,
                                                      @Nullable JsonSchemaObject schema,
                                                      @NotNull JsonSchemaService service,
                                                      @NotNull ProblemsHolder holder,
                                                      @NotNull LocalInspectionToolSession session);
}
