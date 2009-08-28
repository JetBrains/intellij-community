package com.intellij.application.options;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.util.IconLoader;

import java.awt.*;
import java.util.Collection;


public abstract class ImportSchemeAction<T extends Scheme, E extends ExternalizableScheme> extends AnAction {
  protected final SchemesManager<T,E> mySchemesManager;


  public ImportSchemeAction(SchemesManager manager) {
    super("Import", "Import", IconLoader.getIcon("/actions/import.png"));
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
