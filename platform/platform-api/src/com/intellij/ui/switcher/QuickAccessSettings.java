/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ui.switcher;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Set;

public class QuickAccessSettings {
  private final Set<Integer> myModifierVks = new THashSet<>();
  @NonNls public static final String SWITCH_UP = "SwitchUp";
  @NonNls public static final String SWITCH_DOWN = "SwitchDown";
  @NonNls public static final String SWITCH_LEFT = "SwitchLeft";
  @NonNls public static final String SWITCH_RIGHT = "SwitchRight";
  @NonNls public static final String SWITCH_APPLY = "SwitchApply";
  private RegistryValue myModifiersValue;

  public QuickAccessSettings() {
    myModifiersValue = Registry.get("actionSystem.quickAccessModifiers");
    myModifiersValue.addListener(new RegistryValueListener.Adapter() {
      @Override
      public void afterValueChanged(RegistryValue value) {
        applyModifiersFromRegistry();
      }
    }, ApplicationManager.getApplication());

    applyModifiersFromRegistry();
  }

  Keymap getKeymap() {
    return KeymapManager.getInstance().getActiveKeymap();
  }

  void saveModifiersToRegistry(Set<String> codeTexts) {
    StringBuilder value = new StringBuilder();
    for (String each : codeTexts) {
      if (value.length() > 0) {
        value.append(" ");
      }
      value.append(each);
    }
    myModifiersValue.setValue(value.toString());
  }

  private void applyModifiersFromRegistry() {
    Application app = ApplicationManager.getApplication();
    if (app != null && app.isUnitTestMode()) {
      return;
    }

    Set<String> vksSet = new THashSet<>();
    ContainerUtil.addAll(vksSet, getModifierRegistryValue().split(" "));
    myModifierVks.clear();
    int mask = getModifierMask(vksSet);
    myModifierVks.addAll(getModifiersVKs(mask));

    reassignActionShortcut(SWITCH_UP, mask, KeyEvent.VK_UP);
    reassignActionShortcut(SWITCH_DOWN, mask, KeyEvent.VK_DOWN);
    reassignActionShortcut(SWITCH_LEFT, mask, KeyEvent.VK_LEFT);
    reassignActionShortcut(SWITCH_RIGHT, mask, KeyEvent.VK_RIGHT);
    reassignActionShortcut(SWITCH_APPLY, mask, KeyEvent.VK_ENTER);
  }

  @NotNull
  private String getModifierRegistryValue() {
    String value = myModifiersValue.asString().trim();
    if (value.length() > 0) {
      return value;
    }
    return SystemInfo.isMac ? "control alt" : "shift alt";
  }

  private void reassignActionShortcut(String actionId, @JdkConstants.InputEventMask int modifiers, int actionCode) {
    removeShortcuts(actionId);
    if (modifiers > 0) {
      getKeymap().addShortcut(actionId, new KeyboardShortcut(KeyStroke.getKeyStroke(actionCode, modifiers), null));
    }
  }

  private void removeShortcuts(String actionId) {
    Shortcut[] shortcuts = getKeymap().getShortcuts(actionId);
    for (Shortcut each : shortcuts) {
      if (each instanceof KeyboardShortcut) {
        getKeymap().removeShortcut(actionId, each);
      }
    }
  }

  @JdkConstants.InputEventMask
  int getModifierMask(Set<String> codeTexts) {
    int mask = 0;
    for (String each : codeTexts) {
      if ("control".equals(each)) {
        mask |= InputEvent.CTRL_MASK;
      }
      else if ("shift".equals(each)) {
        mask |= InputEvent.SHIFT_MASK;
      }
      else if ("alt".equals(each)) {
        mask |= InputEvent.ALT_MASK;
      }
      else if ("meta".equals(each)) {
        mask |= InputEvent.META_MASK;
      }
    }

    return mask;
  }

  @NotNull
  public static Set<Integer> getModifiersVKs(int mask) {
    Set<Integer> codes = new THashSet<>();
    if ((mask & InputEvent.SHIFT_MASK) > 0) {
      codes.add(KeyEvent.VK_SHIFT);
    }
    if ((mask & InputEvent.CTRL_MASK) > 0) {
      codes.add(KeyEvent.VK_CONTROL);
    }

    if ((mask & InputEvent.META_MASK) > 0) {
      codes.add(KeyEvent.VK_META);
    }

    if ((mask & InputEvent.ALT_MASK) > 0) {
      codes.add(KeyEvent.VK_ALT);
    }

    return codes;
  }

  public static QuickAccessSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(QuickAccessSettings.class);
  }

  public boolean isEnabled() {
    return Registry.is("actionSystem.quickAccessEnabled");
  }

  public Set<Integer> getModiferCodes() {
    return myModifierVks;
  }
}
