// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.codeInsight.documentation.render.DocRenderItem;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;

import java.util.Collections;
import java.util.List;

public interface DocumentationActionProvider {
  ExtensionPointName<DocumentationActionProvider> EP_NAME = ExtensionPointName.create("com.intellij.documentationActionProvider");

  default List<AnAction> additionalActions(DocRenderItem item) {
    return Collections.emptyList();
  }

  default List<AnAction> additionalActions(DocumentationComponent component) {
    return Collections.emptyList();
  }
}
