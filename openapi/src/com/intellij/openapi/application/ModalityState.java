/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.application;

import java.awt.*;

public abstract class ModalityState {
  public static final ModalityState NON_MMODAL = ApplicationManager.getApplication().getNoneModalityState();

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
