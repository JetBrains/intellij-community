// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiDocCommentBase;

import java.util.Collections;
import java.util.List;

public interface DocumentationActionProvider {
  ExtensionPointName<DocumentationActionProvider> EP_NAME = ExtensionPointName.create("com.intellij.documentationActionProvider");

  /**
   * Allows to add custom actions to the context menu of a rendered documentation inlay.
   */
  default List<AnAction> additionalActions(Editor editor, PsiDocCommentBase docComment, String renderedText) {
    return Collections.emptyList();
  }

  /**
   * Allows to add custom actions to Quick Documentation popup and Documentation toolwindow.
   * Actions are added to the context menu and to the toolbar or corner button menu.
   *
   * @deprecated Unused in v2 implementation. Add actions to {@code Documentation.PrimaryGroup} by registering them in plugin.xml as follows:
   * <pre>
   * &lt;action id="..." class="...">
   *  &lt;add-to-group group-id="Documentation.PrimaryGroup" anchor="last"/>
   * &lt;/action>
   * </pre>
   */
  @Deprecated(forRemoval = true)
  default List<AnAction> additionalActions(DocumentationComponent component) {
    return Collections.emptyList();
  }
}
