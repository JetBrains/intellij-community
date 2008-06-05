package com.intellij.application.options;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.WriteExternalException;

public abstract class ExportSchemeAction<T extends Scheme> extends AnAction {
  protected final SchemesManager<T> mySchemesManager;

  public ExportSchemeAction(SchemesManager<T> manager) {
    super("Export", "Export", IconLoader.getIcon("/actions/export.png"));
    mySchemesManager = manager;
  }

  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    T profile = getSelectedScheme();
    presentation.setEnabled(profile != null && isAvailableFor(profile));
  }

  protected abstract T getSelectedScheme();

  private boolean isAvailableFor(final T selected) {
    return selected != null && !mySchemesManager.isShared(selected);
  }

  public void actionPerformed(AnActionEvent e) {
    try {
      mySchemesManager.exportScheme(getSelectedScheme());
    }
    catch (WriteExternalException e1) {
      Messages.showErrorDialog("Cannot export profile: " + e1.getLocalizedMessage(), "Export Profile");
    }
  }
}
