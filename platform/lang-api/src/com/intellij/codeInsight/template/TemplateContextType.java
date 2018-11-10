// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.VolatileNullableLazyValue;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class TemplateContextType {
  public static final ExtensionPointName<TemplateContextType> EP_NAME = ExtensionPointName.create("com.intellij.liveTemplateContext");

  @NotNull
  private final String myContextId;
  @NotNull
  private final String myPresentableName;
  private final NullableLazyValue<TemplateContextType> myBaseContextType;

  protected TemplateContextType(@NotNull @NonNls String id, @NotNull String presentableName) {
    this(id, presentableName, EverywhereContextType.class);
  }

  protected TemplateContextType(@NotNull @NonNls String id,
                                @NotNull String presentableName,
                                @Nullable Class<? extends TemplateContextType> baseContextType) {
    myContextId = id;
    myPresentableName = presentableName;
    myBaseContextType = VolatileNullableLazyValue.createValue(() -> baseContextType == null ? null : EP_NAME.findExtension(baseContextType));
  }

  @NotNull
  public String getPresentableName() {
    return myPresentableName;
  }

  @NotNull
  public String getContextId() {
    return myContextId;
  }

  public abstract boolean isInContext(@NotNull PsiFile file, int offset);

  /**
   * @return whether an abbreviation of this context's template can be entered in editor
   * and expanded from there by Insert Live Template action
   */
  public boolean isExpandableFromEditor() {
    return true;
  }

  @Nullable
  public SyntaxHighlighter createHighlighter() {
    return null;
  }

  @Nullable
  public TemplateContextType getBaseContextType() {
    return myBaseContextType.getValue();
  }

  public Document createDocument(CharSequence text, Project project) {
    return EditorFactory.getInstance().createDocument(text);
  }
}
