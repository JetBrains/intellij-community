// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.JsonSchemaGotoDeclarationSuppressor;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public final class JsonSchemaGotoDeclarationHandler implements GotoDeclarationHandler {
  @Override
  public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
    boolean shouldSuppressNavigation =
      ContainerUtil.exists(JsonSchemaGotoDeclarationSuppressor.EP_NAME.getExtensionList(), it -> it.shouldSuppressGtd(sourceElement));
    if (shouldSuppressNavigation) return null;

    final IElementType elementType = PsiUtilCore.getElementType(sourceElement);
    if (elementType != JsonElementTypes.STRING_LITERAL && elementType != JsonElementTypes.DOUBLE_QUOTED_STRING && elementType != JsonElementTypes.SINGLE_QUOTED_STRING) return null;
    final JsonStringLiteral literal = PsiTreeUtil.getParentOfType(sourceElement, JsonStringLiteral.class, false);
    if (literal == null) return null;
    final PsiElement parent = literal.getParent();
    if (literal.getReferences().length == 0
        && parent instanceof JsonProperty parentProperty
        && parentProperty.getNameElement() == literal
        && canNavigateToSchema(parentProperty)) {
      final PsiFile containingFile = literal.getContainingFile();
      final JsonSchemaService service = JsonSchemaService.Impl.get(literal.getProject());
      final VirtualFile file = containingFile.getVirtualFile();
      if (file == null || !service.isApplicableToFile(file)) return null;
      final JsonPointerPosition steps = JsonOriginalPsiWalker.INSTANCE.findPosition(literal, true);
      if (steps == null) return null;
      final JsonSchemaObject schemaObject = service.getSchemaObject(containingFile);
      if (schemaObject != null) {
        final PsiElement target = new JsonSchemaResolver(sourceElement.getProject(), schemaObject, steps, JsonOriginalPsiWalker.INSTANCE.createValueAdapter(parentProperty.getParent()))
          .findNavigationTarget(parentProperty.getValue());
        if (target != null) {
          return new PsiElement[] {target};
        }
      }
    }
    return null;
  }

  private static boolean canNavigateToSchema(PsiElement parent) {
    return Arrays.stream(parent.getReferences()).noneMatch(r -> r instanceof FileReference);
  }
}
