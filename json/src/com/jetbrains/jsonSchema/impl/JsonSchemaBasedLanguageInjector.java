// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.ThreeState;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class JsonSchemaBasedLanguageInjector extends JsonSchemaInjectorBase {
  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    if (!(context instanceof JsonStringLiteral)) return;
    Project project = context.getProject();
    VirtualFile file = context.getContainingFile().getVirtualFile();
    if (file == null) return;
    JsonSchemaObject schemaObject = JsonSchemaService.Impl.get(project).getSchemaObject(file);
    if (schemaObject == null) return;
    JsonOriginalPsiWalker walker = JsonOriginalPsiWalker.INSTANCE;
    if (walker.isName(context) != ThreeState.NO) return;
    final JsonPointerPosition position = walker.findPosition(context, true);
    if (position == null || position.isEmpty()) return;
    final Collection<JsonSchemaObject> schemas = new JsonSchemaResolver(project, schemaObject, false, position).resolve();
    if (schemas.size() != 1) return;
    JsonSchemaObject object = schemas.iterator().next();
    String injection = object.getLanguageInjection();
    if (injection == null) return;
    Language language = Language.findLanguageByID(injection);
    if (language == null) return;
    injectForHost(registrar, (JsonStringLiteral)context, language);
  }
}
