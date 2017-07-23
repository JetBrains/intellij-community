/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

/**
 * @author Irina.Chernushina on 3/31/2016.
 */
public class JsonSchemaRefReferenceProvider extends PsiReferenceProvider {

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    return new PsiReference[] {new JsonSchemaRefReference((JsonValue)element)};
  }

  private static class JsonSchemaRefReference extends JsonSchemaBaseReference<JsonValue> {
    public JsonSchemaRefReference(JsonValue element) {
      super(element, ElementManipulators.getValueTextRange(element));
    }

    @NotNull
    @Override
    public String getCanonicalText() {
      return StringUtil.unquoteString(super.getCanonicalText());
    }

    @Nullable
    @Override
    public PsiElement resolveInner() {
      final String text = getCanonicalText();

      final JsonSchemaService service = JsonSchemaService.Impl.get(getElement().getProject());
      final JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter splitter = new JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter(text);
      VirtualFile schemaFile = getElement().getContainingFile().getVirtualFile();
      if (splitter.isAbsolute()) {
        assert splitter.getSchemaId() != null;
        schemaFile = service.findSchemaFileByReference(splitter.getSchemaId(), schemaFile);
        if (schemaFile == null) return null;
      }

      final String normalized = JsonSchemaService.normalizeId(splitter.getRelativePath());
      if (StringUtil.isEmptyOrSpaces(normalized) || StringUtil.split(normalized.replace("\\", "/"), "/").size() == 0) {
        return myElement.getManager().findFile(schemaFile);
      }
      final List<String> chain = StringUtil.split(normalized.replace("\\", "/"), "/");
      final Iterator<String> iterator = chain.iterator();
      boolean canSkip = true;
      while (iterator.hasNext()) {
        final String step = iterator.next();
        if (canSkip && JsonSchemaObject.PROPERTIES.equals(step)) {
          iterator.remove();
          canSkip = false;
        } else canSkip = true;
      }

      final JsonSchemaObject schemaObject = service.getSchemaObjectForSchemaFile(schemaFile);
      if (schemaObject == null) return null;

      final List<JsonSchemaVariantsTreeBuilder.Step> steps = JsonSchemaVariantsTreeBuilder.buildSteps(StringUtil.join(chain, "/"));
      return new JsonSchemaResolver(schemaObject, true, steps).findNavigationTarget(true, false);
    }
  }
}
