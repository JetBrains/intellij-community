package com.intellij.application.options;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.WriteExternalException;

public abstract class ExportSchemeAction<T extends Scheme, E extends ExternalizableScheme> extends AnAction {
  protected final SchemesManager<T, E> mySchemesManager;

  public ExportSchemeAction(SchemesManager<T, E> manager) {
    super("Export", "Export", IconLoader.getIcon("/actions/export.png"));
    mySchemesManager = manager;
  }

  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    E profile = getSelectedScheme();
    presentation.setEnabled(profile != null && isAvailableFor(profile));
  }

  protected abstract E getSelectedScheme();

  private boolean isAvailableFor(final E selected) {
    return selected != null && !mySchemesManager.isShared(selected);
  }

  public void actionPerformed(AnActionEvent e) {

    doExport(getSelectedScheme(), mySchemesManager);
  }

  public static <T extends Scheme, E extends ExternalizableScheme> void doExport(final E scheme, SchemesManager<T,E> manager) {
    if (scheme != null) {
      try {
        manager.exportScheme(scheme);

        Messages.showMessageDialog("Scheme '" + scheme.getName() + "' was exported successfully", "Export", Messages.getInformationIcon());
      }
      catch (WriteExternalException e1) {
        Messages.showErrorDialog("Cannot export profile: " + e1.getLocalizedMessage(), "Export Profile");
      }
    }
  }
}
