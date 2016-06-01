/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.tests.gui.fixtures.newProjectWizard;

import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ConfigureFormFactorStepFixture extends AbstractWizardStepFixture<ConfigureFormFactorStepFixture> {
  protected ConfigureFormFactorStepFixture(@NotNull Robot robot, @NotNull JRootPane target) {
    super(ConfigureFormFactorStepFixture.class, robot, target);
  }

  //@NotNull
  //public ConfigureFormFactorStepFixture selectMinimumSdkApi(@NotNull final FormFactor formFactor, @NotNull final String api) {
  //  JCheckBox checkBox = robot().finder().find(target(), new GenericTypeMatcher<JCheckBox>(JCheckBox.class) {
  //    @Override
  //    protected boolean isMatching(@NotNull JCheckBox checkBox) {
  //      String text = checkBox.getText();
  //      // "startsWith" instead of "equals" because the UI may add "(Not installed)" at the end.
  //      return text != null && text.startsWith(formFactor.toString());
  //    }
  //  });
  //  AbstractButtonDriver buttonDriver = new AbstractButtonDriver(robot());
  //  buttonDriver.requireEnabled(checkBox);
  //  buttonDriver.select(checkBox);
  //
  //  final JComboBox comboBox = robot().finder().findByName(target(), formFactor.id + ".minSdk", JComboBox.class);
  //  //noinspection ConstantConditions
  //  int itemIndex = execute(new GuiQuery<Integer>() {
  //    @Override
  //    protected Integer executeInEDT() throws Throwable {
  //      BasicJComboBoxCellReader cellReader = new BasicJComboBoxCellReader();
  //      int itemCount = comboBox.getItemCount();
  //      for (int i = 0; i < itemCount; i++) {
  //        String value = cellReader.valueAt(comboBox, i);
  //        if (value != null && value.startsWith("API " + api + ":")) {
  //          return i;
  //        }
  //      }
  //      return -1;
  //    }
  //  });
  //  if (itemIndex < 0) {
  //    throw new LocationUnavailableException("Unable to find SDK " + api + " in " + formFactor + " drop-down");
  //  }
  //  JComboBoxDriver comboBoxDriver = new JComboBoxDriver(robot());
  //  comboBoxDriver.selectItem(comboBox, itemIndex);
  //  return this;
  //}
}
