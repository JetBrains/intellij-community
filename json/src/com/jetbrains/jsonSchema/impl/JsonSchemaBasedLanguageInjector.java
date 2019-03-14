// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class JsonSchemaBasedLanguageInjector extends JsonSchemaInjectorBase {
  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    if (!(context instanceof JsonStringLiteral)) return;
    Language language = getLanguageToInject(context);
    if (language == null) return;
    injectForHost(registrar, (JsonStringLiteral)context, language);
  }

  @Nullable
  public static Language getLanguageToInject(@NotNull PsiElement context) {
    Project project = context.getProject();
    PsiFile containingFile = context.getContainingFile();
    JsonSchemaObject schemaObject = JsonSchemaService.Impl.get(project).getSchemaObject(containingFile);
    if (schemaObject == null) return null;
    JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(context, schemaObject);
    if (walker == null || walker.isName(context) != ThreeState.NO) return null;
    final JsonPointerPosition position = walker.findPosition(context, true);
    if (position == null || position.isEmpty()) return null;
    final Collection<JsonSchemaObject> schemas = new JsonSchemaResolver(project, schemaObject, false, position).resolve();
    if (schemas.size() != 1) return null;
    JsonSchemaObject object = schemas.iterator().next();
    String injection = object.getLanguageInjection();
    if (injection == null) return null;
    Language language = Language.findLanguageByID(injection);
    if (language == null) return null;
    return language;
  }
}
