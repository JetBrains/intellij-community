// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.VolatileNullableLazyValue;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.NlsContexts.Label;

/**
 * @author yole
 */
public abstract class TemplateContextType {
  public static final ExtensionPointName<TemplateContextType> EP_NAME = ExtensionPointName.create("com.intellij.liveTemplateContext");

  @NotNull
  private final String myContextId;
  @NotNull
  private final @Label String myPresentableName;
  private final VolatileNullableLazyValue<TemplateContextType> myBaseContextType;

  protected TemplateContextType(@NotNull @NonNls String id, @Label @NotNull String presentableName) {
    this(id, presentableName, EverywhereContextType.class);
  }

  protected TemplateContextType(@NotNull @NonNls String id,
                                @Label @NotNull String presentableName,
                                @Nullable Class<? extends TemplateContextType> baseContextType) {
    myContextId = id;
    myPresentableName = presentableName;
    myBaseContextType =
      VolatileNullableLazyValue.createValue(() -> baseContextType == null ? null : EP_NAME.findExtension(baseContextType));
  }

  /**
   * @return context presentable name for templates editor
   */
  @NotNull
  public @Label String getPresentableName() {
    return myPresentableName;
  }

  /**
   * @return unique ID to be used on configuration files to flag if this context is enabled for particular template
   */
  @NotNull
  public @NonNls String getContextId() {
    return myContextId;
  }

  /**
   * @deprecated use {@link #isInContext(TemplateActionContext)}
   */
  @Deprecated
  public boolean isInContext(@NotNull PsiFile file, int offset) {
    throw new RuntimeException("Please, implement isInContext(TemplateActionContext) method and don't invoke this method directly");
  }

  /**
   * @return true iff this context type permits using template associated with it according to {@code templateActionContext}
   */
  public boolean isInContext(@NotNull TemplateActionContext templateActionContext) {
    return isInContext(templateActionContext.getFile(), templateActionContext.getStartOffset());
  }

  /**
   * @return whether an abbreviation of this context's template can be entered in editor
   * and expanded from there by Insert Live Template action
   */
  public boolean isExpandableFromEditor() {
    return true;
  }

  /**
   * @return syntax highlighter that going to be used in live template editor for template with context type enabled. If several context
   * types are enabled - first registered wins.
   */
  @Nullable
  public SyntaxHighlighter createHighlighter() {
    return null;
  }

  /**
   * @return parent context type. Parent context serves two purposes:
   * <ol>
   *   <li>Context types hierarchy shown as a tree in template editor</li>
   *   <li>When template applicability is computed, IDE finds all deepest applicable context types for the current {@link TemplateActionContext}
   *   and excludes checking of all of their parent contexts. Then, IDE checks that at least one of these deepest applicable contexts is
   *   enabled for the template.</li>
   * </ol>
   */
  @Nullable
  public TemplateContextType getBaseContextType() {
    return myBaseContextType.getValue();
  }

  @ApiStatus.Internal
  public void clearCachedBaseContextType() {
    myBaseContextType.drop();
  }

  /**
   * @return document for live template editor. Used for live templates with this context type enabled. If several context types are enabled -
   * first registered wins.
   */
  public Document createDocument(CharSequence text, Project project) {
    return EditorFactory.getInstance().createDocument(text);
  }
}
