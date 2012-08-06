/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.application.options.codeStyle.arrangement;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

/**
 * // TODO den add doc
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/6/12 4:17 PM
 */
public class ArrangementMatcherSettingComponent extends JPanel {

  @NotNull private final ArrangementMatcherPredefinedSettingModel myModel;

  @NotNull private final JCheckBox                        myNegateCheckBox = new JCheckBox("not"); // TODO den i18n
  @NotNull private final JComboBox myKeyComboBox    = new JComboBox();
  @NotNull private final JComboBox myValueComboBox  = new JComboBox();

  public ArrangementMatcherSettingComponent(@NotNull ArrangementMatcherPredefinedSettingModel model) {
    myModel = model;

    for (ArrangementMatcherKey key : model.getAvailableKeys()) {
      myKeyComboBox.addItem(key);
    }

    for (Object o : model.getAvailableValues((ArrangementMatcherKey)myKeyComboBox.getSelectedItem())) {
      myValueComboBox.addItem(o);
    }

    add(myNegateCheckBox);
    add(myKeyComboBox);
    add(myValueComboBox);
  }
}
