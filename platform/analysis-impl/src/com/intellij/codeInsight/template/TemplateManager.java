// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.util.PairProcessor;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public abstract class TemplateManager {
  @Topic.ProjectLevel
  public static final Topic<TemplateManagerListener> TEMPLATE_STARTED_TOPIC = new Topic<>("TEMPLATE_STARTED", TemplateManagerListener.class, Topic.BroadcastDirection.NONE);

  public static TemplateManager getInstance(Project project) {
    return project.getService(TemplateManager.class);
  }

  /**
   * Same as {@link #startTemplate(Editor, Template)}.
   *
   * @return state of the started template
   */
  public abstract @NotNull TemplateState runTemplate(@NotNull Editor editor, @NotNull Template template);

  public abstract void startTemplate(@NotNull Editor editor, @NotNull Template template);

  public abstract void startTemplate(@NotNull Editor editor, String selectionString, @NotNull Template template);

  public abstract void startTemplate(@NotNull Editor editor, @NotNull Template template, TemplateEditingListener listener);

  public abstract void startTemplate(@NotNull final Editor editor,
                                     @NotNull final Template template,
                                     boolean inSeparateCommand,
                                     Map<String, String> predefinedVarValues,
                                     @Nullable TemplateEditingListener listener);

  public abstract void startTemplate(@NotNull Editor editor,
                                     @NotNull Template template,
                                     TemplateEditingListener listener,
                                     final PairProcessor<? super String, ? super String> callback);

  public abstract boolean startTemplate(@NotNull Editor editor, char shortcutChar);

  public abstract Template createTemplate(@NotNull String key, String group);

  public abstract Template createTemplate(@NotNull String key, @NotNull String group, @NonNls String text);

  @Nullable
  public abstract Template getActiveTemplate(@NotNull Editor editor);

  /**
   * Finished a live template in the given editor, if it's present
   * @return whether a live template was present
   */
  public abstract boolean finishTemplate(@NotNull Editor editor);
}
