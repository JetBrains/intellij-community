// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.json.JsonBundle;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.impl.JsonRecursiveElementVisitor;
import com.intellij.jsonpath.psi.JsonPathId;
import com.intellij.jsonpath.psi.JsonPathIdSegment;
import com.intellij.jsonpath.psi.JsonPathVisitor;
import com.intellij.jsonpath.ui.JsonPathEvaluateManager;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

final class JsonPathEvaluateUnknownKeyInspection extends LocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    Supplier<JsonFile> jsonFileSupplier = holder.getFile().getUserData(JsonPathEvaluateManager.JSON_PATH_EVALUATE_SOURCE_KEY);
    JsonFile sourceFile = jsonFileSupplier != null ? jsonFileSupplier.get() : null;
    if (sourceFile == null) return PsiElementVisitor.EMPTY_VISITOR;

    NotNullLazyValue<Set<String>> allKeys = NotNullLazyValue.lazy(() -> collectAllKeysFromFile(sourceFile));

    return new JsonPathVisitor() {
      @Override
      public void visitIdSegment(@NotNull JsonPathIdSegment segment) {
        super.visitIdSegment(segment);

        JsonPathId identifier = segment.getId();
        String idString = identifier.getText();

        if (StringUtil.isNotEmpty(idString) &&
            !allKeys.getValue().contains(idString)) {
          holder.registerProblem(identifier, null, JsonBundle.message("inspection.message.jsonpath.unknown.key", idString));
        }
      }
    };
  }

  private Set<String> collectAllKeysFromFile(JsonFile file) {
    Set<String> propertyNames = new HashSet<>();
    file.accept(new JsonRecursiveElementVisitor() {
      @Override
      public void visitProperty(@NotNull JsonProperty o) {
        super.visitProperty(o);

        propertyNames.add(o.getName());
      }
    });

    return propertyNames;
  }
}
