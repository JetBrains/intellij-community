package com.intellij.ide.customize;

import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public interface CustomizeIDEWizardDialogInterface extends ActionListener {
    void show();

    boolean showIfNeeded();

    @Override
    void actionPerformed(@NotNull ActionEvent e);

    void doCancelAction();
}
