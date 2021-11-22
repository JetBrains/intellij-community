// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionProfileEntry;

/**
 * @author Bas Leijdekkers
 */
public class MultipleCheckboxOptionsPanel extends InspectionOptionsPanel {

    public MultipleCheckboxOptionsPanel(final InspectionProfileEntry owner) {
        super(owner);
    }

    public MultipleCheckboxOptionsPanel(final OptionAccessor optionAccessor) {
      super(optionAccessor);
    }
}