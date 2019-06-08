// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.util.messages.Topic;

@Service
public final class PowerSaveMode {
  public static final Topic<Listener> TOPIC = new Topic<>("PowerSaveMode.Listener", Listener.class);

  private static final String POWER_SAVE_MODE = "power.save.mode";

  public static boolean isEnabled() {
    return PropertiesComponent.getInstance().getBoolean(POWER_SAVE_MODE);
  }

  public static void setEnabled(boolean value) {
    PropertiesComponent propertyComponent = PropertiesComponent.getInstance();
    boolean isEnabled = propertyComponent.getBoolean(POWER_SAVE_MODE);
    if (isEnabled != value) {
      propertyComponent.setValue(POWER_SAVE_MODE, value);
      ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).powerSaveStateChanged();
    }
  }

  public interface Listener {
    void powerSaveStateChanged();
  }
}
