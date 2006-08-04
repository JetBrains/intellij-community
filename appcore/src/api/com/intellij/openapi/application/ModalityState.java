/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.application;

import java.awt.*;

/**
 * Represents the stack of active modal dialogs.
 */
public abstract class ModalityState {
  @Deprecated public static final ModalityState NON_MMODAL = ApplicationManager.getApplication().getNoneModalityState();
  public static final ModalityState NON_MODAL = ApplicationManager.getApplication().getNoneModalityState();

  public static ModalityState current() {
    return ApplicationManager.getApplication().getCurrentModalityState();
  }

  public static ModalityState stateForComponent(Component component){
    return ApplicationManager.getApplication().getModalityStateForComponent(component);
  }

  public static ModalityState defaultModalityState() {
    return ApplicationManager.getApplication().getDefaultModalityState();
  }

  public abstract boolean dominates(ModalityState anotherState);
}
