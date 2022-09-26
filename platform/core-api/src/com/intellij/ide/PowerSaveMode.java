// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.diagnostic.LoadingStateUtilKt;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.util.messages.Topic;

@Service
public final class PowerSaveMode {
  public static final Topic<Listener> TOPIC = new Topic<>(Listener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);

  private static final String POWER_SAVE_MODE = "power.save.mode";

  public static boolean isEnabled() {
    return LoadingStateUtilKt.getAreComponentsInitialized() && PropertiesComponent.getInstance().getBoolean(POWER_SAVE_MODE);
  }

  public static void setEnabled(boolean value) {
    if (!LoadingStateUtilKt.getAreComponentsInitialized()) return;
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
