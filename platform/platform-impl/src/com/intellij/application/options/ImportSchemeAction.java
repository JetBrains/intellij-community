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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.util.PlatformIcons;

import java.awt.*;
import java.util.Collection;


public abstract class ImportSchemeAction<T extends Scheme, E extends ExternalizableScheme> extends AnAction {
  protected final SchemesManager<T,E> mySchemesManager;


  public ImportSchemeAction(SchemesManager manager) {
    super("Import", "Import", PlatformIcons.IMPORT_ICON);
    mySchemesManager = manager;
  }

  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(true);
  }

  public void actionPerformed(AnActionEvent e) {
    SchemesToImportPopup<T,E> popup = new SchemesToImportPopup<T,E>(getPanel()){
      protected void onSchemeSelected(final E scheme) {
        if (scheme != null) {
          importScheme(scheme);

        }

      }
    };
    popup.show(mySchemesManager, collectCurrentSchemes());

  }

  protected abstract Collection<T> collectCurrentSchemes();

  protected abstract Component getPanel();

  protected abstract void importScheme(E scheme);
}
