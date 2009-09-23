
package com.intellij.codeInsight.template;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.PairProcessor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TemplateManager {
  public static TemplateManager getInstance(Project project) {
    return project.getComponent(TemplateManager.class);
  }

  public abstract void startTemplate(@NotNull Editor editor, @NotNull Template template);

  public abstract void startTemplate(@NotNull Editor editor, String selectionString, @NotNull Template template);

  public abstract void startTemplate(@NotNull Editor editor, @NotNull Template template, TemplateEditingListener listener);

  public abstract void startTemplate(@NotNull Editor editor, @NotNull Template template, TemplateEditingListener listener,
                            final PairProcessor<String, String> callback);

  public abstract boolean startTemplate(@NotNull Editor editor, char shortcutChar);

  public abstract TemplateContextType getContextType(@NotNull PsiFile file, int offset);

  public abstract Template createTemplate(@NotNull String key, String group);

  public abstract Template createTemplate(@NotNull String key, String group, @NonNls String text);

  @Nullable
  public abstract Template getActiveTemplate(@NotNull Editor editor);
}
