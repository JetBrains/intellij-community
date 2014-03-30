/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.application.options;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.WriteExternalException;

import java.io.IOException;

public abstract class ExportSchemeAction<T extends Scheme, E extends ExternalizableScheme> extends AnAction {
  protected final SchemesManager<T, E> mySchemesManager;

  public ExportSchemeAction(SchemesManager<T, E> manager) {
    super("Share", "Share scheme on server", AllIcons.Actions.Export);
    mySchemesManager = manager;
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    E profile = getSelectedScheme();
    presentation.setEnabled(profile != null && isAvailableFor(profile));
  }

  protected abstract E getSelectedScheme();

  private boolean isAvailableFor(final E selected) {
    return selected != null && !mySchemesManager.isShared(selected);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    doExport(getSelectedScheme(), mySchemesManager);
  }

  public static <T extends Scheme, E extends ExternalizableScheme> void doExport(final E scheme, SchemesManager<T,E> manager) {
    if (scheme != null) {
      try {
        ShareSchemeDialog dialog = new ShareSchemeDialog();
        dialog.init(scheme);

        dialog.show();

        if (dialog.isOK()) {
          try {
            manager.exportScheme(scheme, dialog.getName(), dialog.getDescription());
            Messages.showMessageDialog("Scheme '" + scheme.getName() + "' was shared successfully as '" + dialog.getName() + " '", "Share Scheme",
                                       Messages.getInformationIcon());            
          }
          catch (IOException e) {
            Messages.showErrorDialog("Cannot share scheme '" + scheme.getName() + "': " + e.getLocalizedMessage(), "Share Scheme");
          }
        }
      }
      catch (WriteExternalException e1) {
        Messages.showErrorDialog("Cannot share scheme: " + e1.getLocalizedMessage(), "Share Scheme");
      }
    }
  }
}
