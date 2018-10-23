// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JsonSchemaGotoDeclarationHandler implements GotoDeclarationHandler {
  @Nullable
  @Override
  public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
    final IElementType elementType = PsiUtilCore.getElementType(sourceElement);
    if (elementType != JsonElementTypes.DOUBLE_QUOTED_STRING && elementType != JsonElementTypes.SINGLE_QUOTED_STRING) return null;
    final JsonStringLiteral literal = PsiTreeUtil.getParentOfType(sourceElement, JsonStringLiteral.class);
    if (literal == null) return null;
    final PsiElement parent = literal.getParent();
    if (parent instanceof JsonProperty && ((JsonProperty)parent).getNameElement() == literal) {
      final JsonSchemaService service = JsonSchemaService.Impl.get(literal.getProject());
      final PsiFile containingFile = literal.getContainingFile();
      final VirtualFile file = containingFile.getVirtualFile();
      if (file == null || !service.isApplicableToFile(file)) return null;
      final List<JsonSchemaVariantsTreeBuilder.Step> steps = JsonOriginalPsiWalker.INSTANCE.findPosition(literal, true);
      if (steps == null) return null;
      final JsonSchemaObject schemaObject = service.getSchemaObject(file);
      if (schemaObject != null) {
        final PsiElement target = new JsonSchemaResolver(schemaObject, false, steps)
          .findNavigationTarget(false, ((JsonProperty)parent).getValue(),
                                JsonSchemaService.isSchemaFile(containingFile));
        if (target != null) {
          return new PsiElement[] {target};
        }
      }
    }
    return null;
  }
}
