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

import java.io.IOException;

public abstract class ExportSchemeAction<T extends Scheme, E extends ExternalizableScheme> extends AnAction {
  protected final SchemesManager<T, E> mySchemesManager;

  public ExportSchemeAction(SchemesManager<T, E> manager) {
    super("Share", "Share scheme on server", IconLoader.getIcon("/actions/export.png"));
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
            Messages.showErrorDialog("Cannot share scheme '" + scheme.getName() + "': " + e.getLocalizedMessage(), "Share Shceme");
          }

        }

      }
      catch (WriteExternalException e1) {
        Messages.showErrorDialog("Cannot share scheme: " + e1.getLocalizedMessage(), "Share Scheme");
      }
    }
  }
}
