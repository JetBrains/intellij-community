package com.intellij.ide.customize;

import java.awt.event.ActionListener;

/**
 * Allows customization of initial wizard dialog.
 *
 * Must have a constructor with four params: @NotNull CustomizeIDEWizardStepsProvider stepsProvider, @Nullable StartupUtil.AppStarter appStarter,
 *                                           boolean beforeSplash, boolean afterSplash
 */
public interface CommonCustomizeIDEWizardDialog extends ActionListener {
    void show();

    boolean showIfNeeded();

    void doCancelAction();
}
