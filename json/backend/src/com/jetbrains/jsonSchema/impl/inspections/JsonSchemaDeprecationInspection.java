// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.json.JsonBundle;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.JsonElementVisitor;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaResolver;
import com.jetbrains.jsonSchema.impl.MatchResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JsonSchemaDeprecationInspection extends JsonSchemaBasedInspectionBase {
  @Override
  protected PsiElementVisitor doBuildVisitor(@NotNull JsonValue root,
                                             @Nullable JsonSchemaObject schema,
                                             @NotNull JsonSchemaService service,
                                             @NotNull ProblemsHolder holder,
                                             @NotNull LocalInspectionToolSession session) {
    if (schema == null) return PsiElementVisitor.EMPTY_VISITOR;
    final JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(root, schema);
    if (walker == null) return PsiElementVisitor.EMPTY_VISITOR;
    Project project = root.getProject();
    return new JsonElementVisitor() {
      @Override
      public void visitProperty(@NotNull JsonProperty o) {
        annotate(o);
        super.visitProperty(o);
      }
      private void annotate(@NotNull JsonProperty o) {
        JsonPointerPosition position = walker.findPosition(o, true);
        if (position == null) return;

        JsonPropertyAdapter parentPropertyAdapter = walker.getParentPropertyAdapter(o);
        final MatchResult result = new JsonSchemaResolver(project, schema, position, parentPropertyAdapter == null ? null : parentPropertyAdapter.getNameValueAdapter()).detailedResolve();
        Iterable<JsonSchemaObject> iterable;
        if (result.myExcludingSchemas.size() == 1) {
          iterable = ContainerUtil.concat(result.mySchemas, result.myExcludingSchemas.get(0));
        } else {
          iterable = result.mySchemas;
        }

        for (JsonSchemaObject object : iterable) {
          String message = object.getDeprecationMessage();
          if (message != null) {
            holder.registerProblem(o.getNameElement(), JsonBundle.message("property.0.is.deprecated.1", o.getName(), message));
            return;
          }
        }
      }
    };
  }
}
