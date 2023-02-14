/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.intention;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class EmptyIntentionAction extends AbstractEmptyIntentionAction implements LowPriorityAction, Iconable {
  private final @IntentionFamilyName String myName;

  public EmptyIntentionAction(@NotNull @IntentionFamilyName String name) {
    myName = name;
  }

  @Override
  @NotNull
  public String getText() {
    return AnalysisBundle.message("inspection.options.action.text", myName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return myName;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true; //edit inspection settings is always enabled
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final EmptyIntentionAction that = (EmptyIntentionAction)o;

    return myName.equals(that.myName);
  }

  public int hashCode() {
    return myName.hashCode();
  }

  @Override
  public Icon getIcon(@IconFlags int flags) {
    return isNewUi() ? EmptyIcon.ICON_0 : AllIcons.Actions.RealIntentionBulb;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project,
                                                       @NotNull Editor editor,
                                                       @NotNull PsiFile file) {
    return new IntentionPreviewInfo.Html(AnalysisBundle.message("empty.inspection.action.description", myName));
  }

  // We cannot use here ExperimentalUI.isNewUI() because of module dependencies.
  // Please, modify this code synchronously with ExperimentalUI.isNewUI()
  private static boolean isNewUi() {
    // CWM-7348 thin client does not support new UI
    return (EarlyAccessRegistryManager.INSTANCE.getBoolean("ide.experimental.ui"));
  }
}
