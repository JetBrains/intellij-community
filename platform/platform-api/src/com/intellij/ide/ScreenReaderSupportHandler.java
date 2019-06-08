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

/**
 * Keep {@link ScreenReader#isActive} in sync with {@link GeneralSettings#isSupportScreenReaders}
 */
public final class ScreenReaderSupportHandler implements Disposable {
  public ScreenReaderSupportHandler() {
    GeneralSettings generalSettings = GeneralSettings.getInstance();
    generalSettings.addPropertyChangeListener(GeneralSettings.PROP_SUPPORT_SCREEN_READERS, this, e -> ScreenReader.setActive((Boolean)e.getNewValue()));
    ScreenReader.setActive(generalSettings.isSupportScreenReaders());
  }

  @Override
  public void dispose() {
  }
}
