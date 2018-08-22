// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class JsonRequiredPropsReferenceProvider extends PsiReferenceProvider {
  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    return new PsiReference[] {new JsonRequiredPropReference((JsonStringLiteral)element)};
  }

  @Nullable
  public static JsonObject findPropertiesObject(PsiElement element) {
    PsiElement parent = getParentSafe(getParentSafe(getParentSafe(element)));
    if (!(parent instanceof JsonObject)) return null;
    Optional<JsonProperty> propertiesProp =
      ((JsonObject)parent).getPropertyList().stream().filter(p -> "properties".equals(p.getName())).findFirst();
    if (propertiesProp.isPresent()) {
      JsonValue value = propertiesProp.get().getValue();
      if (value instanceof JsonObject) {
        return (JsonObject)value;
      }
    }
    return null;
  }

  private static PsiElement getParentSafe(@Nullable PsiElement element) {
    return element == null ? null : element.getParent();
  }

  private static class JsonRequiredPropReference extends JsonSchemaBaseReference<JsonStringLiteral> {
    public JsonRequiredPropReference(JsonStringLiteral element) {
      super(element, ElementManipulators.getValueTextRange(element));
    }

    @Nullable
    @Override
    public PsiElement resolveInner() {
      JsonObject propertiesObject = findPropertiesObject(getElement());
      if (propertiesObject != null) {
        String name = getElement().getValue();
        for (JsonProperty property : propertiesObject.getPropertyList()) {
          if (name.equals(property.getName())) return property;
        }
      }
      return null;
    }
  }
}
