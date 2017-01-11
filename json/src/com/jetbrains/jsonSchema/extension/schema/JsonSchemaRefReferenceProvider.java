/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.jsonSchema.extension.schema;

import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import com.jetbrains.jsonSchema.impl.JsonSchemaExportedDefinitions;
import com.jetbrains.jsonSchema.impl.JsonSchemaReader;
import com.jetbrains.jsonSchema.impl.JsonSchemaServiceEx;
import com.jetbrains.jsonSchema.impl.JsonSchemaWalker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

      final JsonSchemaReader.SchemaUrlSplitter splitter = new JsonSchemaReader.SchemaUrlSplitter(text);
      VirtualFile schemaFile = getElement().getContainingFile().getVirtualFile();
      if (splitter.isAbsolute()) {
        assert splitter.getSchemaId() != null;
        schemaFile = JsonSchemaServiceEx.Impl.getEx(getElement().getProject()).getSchemaFileById(splitter.getSchemaId(), schemaFile);
        if (schemaFile == null) return null;
      }
      if (StringUtil.isEmptyOrSpaces(splitter.getRelativePath())) {
        return myElement.getManager().findFile(schemaFile);
      }

      final String normalized = JsonSchemaExportedDefinitions.normalizeId(splitter.getRelativePath());
      final Pair<List<JsonSchemaWalker.Step>, String> steps = JsonSchemaWalker.buildSteps(normalized);
      return new JsonSchemaInsideSchemaResolver(myElement.getProject(), schemaFile, normalized, steps.getFirst())
        .resolveInSchemaRecursively();
    }
  }
}
