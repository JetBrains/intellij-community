// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Intention action replacement that operates on {@link ModCommand}.
 */
public interface ModCommandAction {
  /**
   * Returns the text to be shown in the list of available actions in the intentions popup menu.
   */
  @IntentionName
  @NotNull
  default String getName() {
    return getFamilyName();
  }

  /**
   * Returns the name of the family of intentions.
   * It is used to externalize the "auto-show" state of intentions.
   * When the user clicks on a light bulb in the intention list,
   * all intentions with the same family name get enabled/disabled.
   * The name is also shown in the Settings tree.
   *
   * @return the intention family name.
   */
  @NotNull
  @IntentionFamilyName
  String getFamilyName();
  
  default PriorityAction.@NotNull Priority getPriority() {
    return PriorityAction.Priority.NORMAL;
  }

  boolean isAvailable(@NotNull ActionContext context);
  
  @NotNull ModCommand perform(@NotNull ActionContext context);

  default @NotNull IntentionPreviewInfo generatePreview(@NotNull ActionContext context) {
    ModCommand command = ModCommand.retrieve(() -> perform(context));
    return IntentionPreviewUtils.getModCommandPreview(command, context.file());
  }
  
  default @NotNull IntentionAction asIntention() {
    return new ModCommandActionWrapper(this);
  }

  record ActionContext(@NotNull Project project, @NotNull PsiFile file, int offset, @NotNull TextRange selection) {
    
  }
}
