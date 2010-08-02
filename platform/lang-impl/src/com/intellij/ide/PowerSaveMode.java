/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;

/**
 * @author yole
 */
public class PowerSaveMode {
  private boolean myEnabled = false;
  private final MessageBus myBus;

  public PowerSaveMode(MessageBus bus) {
    myBus = bus;
  }

  public static boolean isEnabled() {
    return ServiceManager.getService(PowerSaveMode.class).myEnabled;
  }

  public static void setEnabled(boolean value) {
    final PowerSaveMode instance = ServiceManager.getService(PowerSaveMode.class);
    if (instance.myEnabled != value) {
      instance.myEnabled = value;
      instance.myBus.syncPublisher(TOPIC).powerSaveStateChanged();
    }
  }

  public interface Listener {
    void powerSaveStateChanged();
  }

  public static Topic<Listener> TOPIC = Topic.create("PowerSaveMode.Listener", Listener.class);
}
