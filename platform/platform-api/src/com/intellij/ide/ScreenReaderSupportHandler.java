/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.intellij.ide;

import com.intellij.openapi.Disposable;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;

/**
 * Keep {@link ScreenReader#isActive} in sync with {@link GeneralSettings#isSupportScreenReaders}
 */
public class ScreenReaderSupportHandler implements Disposable {
  private final GeneralSettings mySettings;
  private final PropertyChangeListener myGeneralSettingsListener;

  public ScreenReaderSupportHandler(@NotNull GeneralSettings generalSettings) {
    mySettings = generalSettings;
    myGeneralSettingsListener = e -> {
      if (GeneralSettings.PROP_SUPPORT_SCREEN_READERS.equals(e.getPropertyName())) {
        ScreenReader.setActive((Boolean)e.getNewValue());
      }
    };
    mySettings.addPropertyChangeListener(myGeneralSettingsListener);

    ScreenReader.setActive(mySettings.isSupportScreenReaders());
  }

  @Override
  public void dispose() {
    mySettings.removePropertyChangeListener(myGeneralSettingsListener);
  }
}
