/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.Presentation;

import javax.swing.*;

public interface CustomComponentAction {

  String CUSTOM_COMPONENT_PROPERTY = "customComponent";

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
