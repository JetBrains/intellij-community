/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.application.options.schemes;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.Scheme;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class DescriptionAwareSchemeActions<T extends Scheme> extends AbstractSchemeActions<T> {
  protected DescriptionAwareSchemeActions(@NotNull AbstractDescriptionAwareSchemesPanel<T> schemesPanel) {
    super(schemesPanel);
  }

  @Nullable
  public abstract String getDescription(@NotNull T scheme);

  protected abstract void setDescription(@NotNull T scheme, @NotNull String newDescription);

  @Override
  protected void addAdditionalActions(@NotNull List<AnAction> defaultActions) {
    defaultActions.add(new AnAction("Edit description") {

      @Override
      public void update(AnActionEvent e) {
        T scheme = getSchemesPanel().getSelectedScheme();
        if (scheme == null) {
          e.getPresentation().setEnabledAndVisible(false);
          return;
        }
        final String text = getDescription(scheme) == null ? "Add Description..." : "Edit Description...";
        e.getPresentation().setEnabledAndVisible(true);
        e.getPresentation().setText(text);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        ((AbstractDescriptionAwareSchemesPanel<T>) mySchemesPanel).editDescription(getDescription(getSchemesPanel().getSelectedScheme()));
      }
    });
  }

  @Override
  protected void onSchemeChanged(@Nullable T scheme) {
    if (scheme != null) {
      ((AbstractDescriptionAwareSchemesPanel<T>) mySchemesPanel).showDescription();
    }
  }
}
