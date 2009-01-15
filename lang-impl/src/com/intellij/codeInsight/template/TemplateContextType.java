package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class TemplateContextType {
  public static final ExtensionPointName<TemplateContextType> EP_NAME = ExtensionPointName.create("com.intellij.liveTemplateContext");

  private final String myContextId;
  private final String myPresentableName;

  protected TemplateContextType(@NotNull @NonNls String id, @NotNull String presentableName) {
    myPresentableName = presentableName;
    myContextId = id;
  }

  public String getPresentableName() {
    return myPresentableName;
  }

  public String getContextId() {
    return myContextId;
  }

  public abstract boolean isInContext(@NotNull PsiFile file, int offset);

  public abstract boolean isInContext(@NotNull final FileType fileType);

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

}
