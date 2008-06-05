package com.intellij.application.options;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.util.IconLoader;

import java.awt.*;
import java.util.Collection;


public abstract class ImportSchemeAction<T extends Scheme> extends AnAction {
  protected final SchemesManager<T> mySchemesManager;


  public ImportSchemeAction(SchemesManager manager) {
    super("Import", "Import", IconLoader.getIcon("/actions/import.png"));
    mySchemesManager = manager;
  }

  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(true);
  }

  public void actionPerformed(AnActionEvent e) {
    SchemesToImportPopup<T> popup = new SchemesToImportPopup<T>(getPanel()){
      protected void onSchemeSelected(final T scheme) {
        if (scheme != null) {
          importScheme(scheme);

        }

      }
    };
    popup.show(mySchemesManager, collectCurrentSchemeNames());

  }

  protected abstract Collection<String> collectCurrentSchemeNames();

  protected abstract Component getPanel();

  protected abstract void importScheme(T scheme);
}
