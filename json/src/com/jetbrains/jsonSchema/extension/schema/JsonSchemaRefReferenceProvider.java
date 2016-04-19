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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ProcessingContext;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaResourcesRootsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
      String id = null;
      String ref = text.substring(1);
      if (!text.startsWith("#")) {
        final int idx = text.indexOf("#");
        if (idx <= 0) return null;
        id = text.substring(0, idx);
        ref = text.substring(idx + 1);
      }

      return resolveSchemaProperty(getElement(), id, ref);
    }
  }

  @Nullable
  public static PsiElement resolveSchemaProperty(PsiElement baseElement, @Nullable final String schemaId, String ref) {
    final Project project = baseElement.getProject();
    final FileBasedIndex index = FileBasedIndex.getInstance();
    final Ref<Pair<VirtualFile, Integer>> reference = new Ref<>();
    final GlobalSearchScope filter = schemaId == null ? GlobalSearchScope.fileScope(baseElement.getContainingFile()) :
                                     JsonSchemaResourcesRootsProvider.enlarge(project, GlobalSearchScope.allScope(project));
    index.processValues(JsonSchemaFileIndex.PROPERTIES_INDEX, ref, null, new FileBasedIndex.ValueProcessor<Integer>() {
      @Override
      public boolean process(VirtualFile file, Integer value) {
        if (schemaId != null) {
          if (!JsonSchemaService.Impl.getEx(project).checkFileForId(schemaId, file)) {
            return true;
          }
        }
        reference.set(Pair.create(file, value));
        return false;
      }
    }, filter);

    if (!reference.isNull()) {
      final Pair<VirtualFile, Integer> pair = reference.get();
      final PsiFile file = baseElement.getManager().findFile(pair.getFirst());
      if (file != null) {
        return file.findElementAt(pair.getSecond());
      }
    }
    return null;
  }
}
