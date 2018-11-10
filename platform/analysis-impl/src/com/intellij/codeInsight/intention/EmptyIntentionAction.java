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

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class EmptyIntentionAction extends AbstractEmptyIntentionAction implements LowPriorityAction, Iconable {
  private final String myName;

  public EmptyIntentionAction(@NotNull String name) {
    myName = name;
  }

  @Override
  @NotNull
  public String getText() {
    return InspectionsBundle.message("inspection.options.action.text", myName);
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
    return AllIcons.Actions.RealIntentionBulb;
  }
}
