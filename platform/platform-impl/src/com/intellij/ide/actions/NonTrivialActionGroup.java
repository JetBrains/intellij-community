/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionGroupUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.project.DumbAware;

/**
 * This group hides itself when there's no enabled and visible child.
 *
 * @see com.intellij.ide.actions.SmartPopupActionGroup
 * @see com.intellij.ide.actions.NonEmptyActionGroup
 *
 * @author gregsh
 */
public class NonTrivialActionGroup extends DefaultActionGroup implements DumbAware {
  public NonTrivialActionGroup() {
    super();
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(!ActionGroupUtil.isGroupEmpty(this, e, LaterInvocator.isInModalContext()));
  }
}
