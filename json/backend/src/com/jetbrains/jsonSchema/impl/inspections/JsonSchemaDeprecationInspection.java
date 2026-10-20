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
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaResolver;
import com.jetbrains.jsonSchema.impl.MatchResult;
import com.jetbrains.jsonSchema.impl.MergedJsonSchemaObject;
import com.jetbrains.jsonSchema.impl.light.nodes.InheritedJsonSchemaObjectView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
          iterable = ContainerUtil.concat(result.mySchemas, result.myExcludingSchemas.getFirst());
        } else {
          iterable = result.mySchemas;
        }

        JsonValueAdapter valueAdapter = o.getValue() == null ? null : walker.createValueAdapter(o.getValue());
        String message = getDeprecationMessage(iterable, valueAdapter);
        if (message != null) {
          holder.registerProblem(o.getNameElement(), JsonBundle.message("property.0.is.deprecated.1", o.getName(), message));
        }
      }
    };
  }

  public static @Nullable String getDeprecationMessage(@NotNull Iterable<? extends JsonSchemaObject> schemas,
                                                       @Nullable JsonValueAdapter inspectedValueAdapter) {
    for (JsonSchemaObject schema : schemas) {
      String message = schema.getDeprecationMessage();
      if (message == null) continue;
      JsonSchemaObject alternativeBranch = findDeprecatedAlternativeBranch(schema);
      if (alternativeBranch != null &&
          (inspectedValueAdapter == null || !JsonSchemaResolver.isCorrect(inspectedValueAdapter, alternativeBranch))) {
        continue;
      }
      return message;
    }
    return null;
  }

  private static @Nullable JsonSchemaObject findDeprecatedAlternativeBranch(@NotNull JsonSchemaObject schema) {
    if (!(schema instanceof MergedJsonSchemaObject mergedSchema) || schema instanceof InheritedJsonSchemaObjectView) return null;

    JsonSchemaObject baseSchema = mergedSchema.getBase();
    JsonSchemaObject alternativeSchema = mergedSchema.getOther();
    boolean isDeprecatedAlternative =
      baseSchema.getDeprecationMessage() == null &&
      alternativeSchema.getDeprecationMessage() != null &&
      (isBuiltFromBranch(alternativeSchema, baseSchema.getAnyOf()) || isBuiltFromBranch(alternativeSchema, baseSchema.getOneOf()));
    return isDeprecatedAlternative ? alternativeSchema : null;
  }

  private static boolean isBuiltFromBranch(@NotNull JsonSchemaObject schema, @Nullable List<? extends JsonSchemaObject> branches) {
    if (branches == null) return false;
    if (branches.contains(schema)) return true;
    return schema instanceof MergedJsonSchemaObject mergedSchema &&
           (isBuiltFromBranch(mergedSchema.getBase(), branches) || isBuiltFromBranch(mergedSchema.getOther(), branches));
  }
}
