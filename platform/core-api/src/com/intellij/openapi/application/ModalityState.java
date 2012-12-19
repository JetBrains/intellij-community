/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Represents the stack of active modal dialogs.
 */
public abstract class ModalityState {
  @NotNull public static final ModalityState NON_MODAL;

  static {
    try {
      @SuppressWarnings("unchecked")
      final Class<? extends ModalityState> ex = (Class<? extends ModalityState>)Class.forName("com.intellij.openapi.application.impl.ModalityStateEx");
      NON_MODAL = ex.newInstance();
    }
    catch (ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
    catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
    catch (InstantiationException e) {
      throw new IllegalStateException(e);
    }
  }

  @NotNull
  public static ModalityState current() {
    return ApplicationManager.getApplication().getCurrentModalityState();
  }

  @NotNull
  public static ModalityState any() {
    return ApplicationManager.getApplication().getAnyModalityState();
  }
  
  @NotNull
  public static ModalityState stateForComponent(Component component){
    return ApplicationManager.getApplication().getModalityStateForComponent(component);
  }

  @NotNull
  public static ModalityState defaultModalityState() {
    return ApplicationManager.getApplication().getDefaultModalityState();
  }

  public abstract boolean dominates(@NotNull ModalityState anotherState);
}
