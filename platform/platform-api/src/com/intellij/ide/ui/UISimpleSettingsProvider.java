/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.ui;

import com.intellij.ide.SearchTopHitProvider;
import com.intellij.util.Consumer;

/**
 * @author Konstantin Bulenkov
 */
public class UISimpleSettingsProvider implements SearchTopHitProvider {
  private static UISettingsOptionDescription CYCLING_SCROLLING = new UISettingsOptionDescription("CYCLING_SCROLLING", "Cyclic scrolling", "appearance");
  private static UISettingsOptionDescription MEMORY_INDICATOR = new UISettingsOptionDescription("SHOW_MEMORY_INDICATOR", "Show Memory Indicator", "appearance");


  @Override
  public void consumeTopHits(String pattern, Consumer<Object> collector) {
    pattern = pattern.trim().toLowerCase();
    if (pattern.startsWith("cyc") || pattern.startsWith("scr") || patternContains(pattern, "scroll")) {
      collector.consume(CYCLING_SCROLLING);
    } else if (patternContains(pattern, "memo")) {
      collector.consume(MEMORY_INDICATOR);
    }
  }

  private static boolean patternContains(String pattern, String search) {
    for (String s : pattern.split(" ")) {
      if (s.contains(search)) {
        return true;
      }
    }
    return false;
  }
}
