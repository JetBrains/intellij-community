// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// This is a slightly modified version of test 'tests.detailed.MainFrame' from repository https://github.com/JetBrains/jcef.git
package com.intellij.internal.jcef.test.detailed.ui;

import org.jetbrains.annotations.ApiStatus;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.Dimension;

@ApiStatus.Internal
public class  StatusPanel extends JPanel {
    private final JProgressBar progressBar_;
    private final JLabel status_field_;

    public StatusPanel() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

        add(Box.createHorizontalStrut(5));
        add(Box.createHorizontalStrut(5));

        progressBar_ = new JProgressBar();
        Dimension progressBarSize = progressBar_.getMaximumSize();
        progressBarSize.width = 100;
        progressBar_.setMinimumSize(progressBarSize);
        progressBar_.setMaximumSize(progressBarSize);
        add(progressBar_);
        add(Box.createHorizontalStrut(5));

        status_field_ = new JLabel("Info");
        status_field_.setAlignmentX(LEFT_ALIGNMENT);
        add(status_field_);
        add(Box.createHorizontalStrut(5));
        add(Box.createVerticalStrut(21));
    }

    public void setIsInProgress(boolean inProgress) {
        progressBar_.setIndeterminate(inProgress);
    }

    public void setStatusText(String text) {
        status_field_.setText(text);
    }
}
