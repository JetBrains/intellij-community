package com.intellij.ide.customize;

import java.awt.event.ActionListener;

/**
 * Allows customization of initial wizard dialog.
 *
 * Must have a constructor with one param: @Nullable StartupUtil.AppStarter appStarter
 */
public interface CommonCustomizeIDEWizardDialog extends ActionListener {
    void show();

    boolean showIfNeeded();

    void doCancelAction();

    void setRunAfterOKAction(Runnable runnable);

}
