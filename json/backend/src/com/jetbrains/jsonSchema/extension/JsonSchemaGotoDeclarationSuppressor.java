// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;

/**
 * Extension point aimed to suppress navigation to json schema element which corresponds to given PsiElement
 */
public interface JsonSchemaGotoDeclarationSuppressor {
  ExtensionPointName<JsonSchemaGotoDeclarationSuppressor> EP_NAME = ExtensionPointName.create("com.intellij.json.jsonSchemaGotoDeclarationSuppressor");

  boolean shouldSuppressGtd(PsiElement psiElement);
}
