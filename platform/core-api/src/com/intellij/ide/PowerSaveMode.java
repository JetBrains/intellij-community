// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.Topic;

public final class PowerSaveMode {

  @Topic.AppLevel
  public static final Topic<Listener> TOPIC = new Topic<>(Listener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);

  private static final String POWER_SAVE_MODE = "power.save.mode";

  public static boolean isEnabled() {
    if (LoadingState.COMPONENTS_REGISTERED.isOccurred()) {
      Application app = ApplicationManager.getApplication();
      @SuppressWarnings("SimplifiableServiceRetrieving")
      PropertiesComponent propertyComponent = app == null ? null : app.getService(PropertiesComponent.class);
      if (propertyComponent != null && propertyComponent.getBoolean(POWER_SAVE_MODE)) {
        return true;
      }
    }
    return false;
  }

  public static void setEnabled(boolean value) {
    if (!LoadingState.COMPONENTS_REGISTERED.isOccurred()) {
      return;
    }

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
