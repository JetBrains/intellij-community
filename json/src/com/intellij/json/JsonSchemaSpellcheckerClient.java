// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class JsonSchemaSpellcheckerClient {
  protected abstract @NotNull PsiElement getElement();

  protected abstract @Nullable String getValue();

  public boolean matchesNameFromSchema() {
    final VirtualFile file = PsiUtilCore.getVirtualFile(getElement());
    if (file == null) return false;

    Project project = getElement().getProject();
    final JsonSchemaService service = JsonSchemaService.Impl.get(project);
    if (!service.isApplicableToFile(file)) return false;
    final JsonSchemaObject rootSchema = service.getSchemaObject(getElement().getContainingFile());
    if (rootSchema == null) return false;
    if (isXIntellijInjection(service, rootSchema)) return true;

    String value = getValue();
    if (StringUtil.isEmpty(value)) return false;

    JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(getElement(), rootSchema);
    if (walker == null) return false;
    final PsiElement checkable = walker.findElementToCheck(getElement());
    if (checkable == null) return false;
    final ThreeState isName = walker.isName(checkable);
    final JsonPointerPosition position = walker.findPosition(checkable, isName == ThreeState.NO);
    if (position == null || position.isEmpty() && isName == ThreeState.NO) return false;

    final Collection<JsonSchemaObject> schemas = new JsonSchemaResolver(project, rootSchema, position).resolve();
    if (schemas.isEmpty()) return false;

    return schemas.stream().anyMatch(s -> {
      if (s.getProperties().containsKey(value) || s.getMatchingPatternPropertySchema(value) != null) {
        return true;
      }
      return ContainerUtil.notNullize(s.getEnum()).stream().anyMatch(e -> e instanceof String && StringUtil.unquoteString((String)e).equals(value));
    });
  }

  protected boolean isXIntellijInjection(@NotNull JsonSchemaService service, @NotNull JsonSchemaObject rootSchema) {
    if (service.isSchemaFile(rootSchema)) {
      JsonProperty property = ObjectUtils.tryCast(getElement().getParent(), JsonProperty.class);
      if (property != null) {
        if (JsonSchemaObject.X_INTELLIJ_LANGUAGE_INJECTION.equals(property.getName())) {
          return true;
        }
        if ("language".equals(property.getName())) {
          PsiElement parent = property.getParent();
          if (parent instanceof JsonObject) {
            PsiElement grandParent = parent.getParent();
            if (grandParent instanceof JsonProperty && JsonSchemaObject.X_INTELLIJ_LANGUAGE_INJECTION.equals(((JsonProperty)grandParent).getName())) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }
}
