// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.Presentation;

import javax.swing.*;

public interface CustomComponentAction {

  String CUSTOM_COMPONENT_PROPERTY = "customComponent";
  String CUSTOM_COMPONENT_ACTION_PROPERTY = "customComponentAction";

  /**
   * @return custom JComponent that represents action in UI.
   * You (as a client/implementor) or this interface do not allow to invoke
   * this method directly. Only action system can invoke it!
   * <br/>
   * <br/>
   * The component should not be stored in the action instance because it may
   * be shown on several toolbars simultaneously. CustomComponentAction.CUSTOM_COMPONENT_PROPERTY
   * can be used to retrieve current component from a Presentation in AnAction#update() method.
   */
  JComponent createCustomComponent(Presentation presentation);
}
