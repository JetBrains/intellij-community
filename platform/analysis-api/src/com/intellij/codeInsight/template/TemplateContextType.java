// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.template.LiveTemplateContextBean.EVERYWHERE_CONTEXT_ID;
import static com.intellij.openapi.util.NlsContexts.Label;

/**
 * Implement this class to describe some particular context that the user may associate with a live template, e.g., "Java String Start".
 * Contexts are available for the user in the Live Template management UI.
 */
public abstract class TemplateContextType {
  String myContextId;
  DeferredTemplateContextType myBaseContextType;

  private final @NotNull @Label String myPresentableName;

  protected TemplateContextType(@Label @NotNull String presentableName) {
    myPresentableName = presentableName;
  }

  /**
   * @deprecated Set contextId in plugin.xml instead
   */
  @Deprecated
  protected TemplateContextType(@NotNull String id, @Label @NotNull String presentableName) {
    this(id, presentableName, EverywhereContextType.class);
  }

  /**
   * @deprecated Set contextId and baseContextId in plugin.xml instead
   */
  @Deprecated
  protected TemplateContextType(@NotNull String id,
                                @Label @NotNull String presentableName,
                                @Nullable Class<? extends TemplateContextType> baseContextType) {
    myContextId = id;
    myPresentableName = presentableName;
    myBaseContextType = new ClassTemplateContextTypeCache(baseContextType);
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
  public final String getContextId() {
    if (myContextId == null) {
      throw new AssertionError("contextId must be set for liveTemplateContext " + this);
    }
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
    return myBaseContextType != null ? myBaseContextType.getValue() : null;
  }

  @ApiStatus.Internal
  public void clearCachedBaseContextType() {
    if (myBaseContextType != null) {
      myBaseContextType.drop();
    }
  }

  /**
   * @return document for live template editor. Used for live templates with this context type enabled. If several context types are enabled -
   * first registered wins.
   */
  public Document createDocument(CharSequence text, Project project) {
    return EditorFactory.getInstance().createDocument(text);
  }

  interface DeferredTemplateContextType {
    TemplateContextType getValue();
    void drop();
  }

  static final class TemplateContextTypeCache implements DeferredTemplateContextType {

    public static final TemplateContextTypeCache EVERYWHERE_CONTEXT = new TemplateContextTypeCache(EVERYWHERE_CONTEXT_ID);

    private final @Nullable String myBaseContextTypeId;
    private boolean myComputed;
    private @Nullable TemplateContextType myValue;

    TemplateContextTypeCache(@Nullable String baseContextTypeId) {
      myBaseContextTypeId = baseContextTypeId;
    }

    @Override
    public synchronized @Nullable TemplateContextType getValue() {
      if (!myComputed) {
        myValue = myBaseContextTypeId == null ? null : LiveTemplateContextBean.EP_NAME.getExtensionList().stream()
          .filter(t -> t.getContextId().equals(myBaseContextTypeId))
          .map(BaseKeyedLazyInstance::getInstance)
          .findFirst()
          .orElseThrow();

        myComputed = true;
      }
      return myValue;
    }

    @Override
    public synchronized void drop() {
      myComputed = false;
      myValue = null;
    }
  }

  private static class ClassTemplateContextTypeCache implements DeferredTemplateContextType {
    private final @Nullable Class<? extends TemplateContextType> myBaseContextType;
    private boolean myComputed;
    private @Nullable TemplateContextType myValue;

    private ClassTemplateContextTypeCache(@Nullable Class<? extends TemplateContextType> baseContextType) {
      myBaseContextType = baseContextType;
    }

    @Override
    public synchronized @Nullable TemplateContextType getValue() {
      if (!myComputed) {
        myValue = myBaseContextType == null ? null : LiveTemplateContextBean.EP_NAME.getExtensionList().stream()
          .filter(t -> myBaseContextType.getName().equals(t.implementationClass))
          .map(BaseKeyedLazyInstance::getInstance)
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("Unable to find base context type with implementation class " + myBaseContextType));

        myComputed = true;
      }
      return myValue;
    }

    @Override
    public synchronized void drop() {
      myComputed = false;
      myValue = null;
    }
  }
}
