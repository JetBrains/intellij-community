/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.tests.gui.fixtures;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.google.common.base.Strings.nullToEmpty;
import static com.intellij.tests.gui.framework.GuiTests.waitUntilFound;
import static org.fest.swing.edt.GuiActionRunner.execute;

class MessageDialogFixture extends IdeaDialogFixture<DialogWrapper> implements MessagesFixture.Delegate {
  @NotNull
  static MessageDialogFixture findByTitle(@NotNull Robot robot, @NotNull final String title) {
    final Ref<DialogWrapper> wrapperRef = new Ref<DialogWrapper>();
    JDialog dialog = waitUntilFound(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
          if (!title.equals(dialog.getTitle()) || !dialog.isShowing()) {
            return false;
          }
          DialogWrapper wrapper = getDialogWrapperFrom(dialog, DialogWrapper.class);
          if (wrapper != null) {
            String typeName = Messages.class.getName() + "$MessageDialog";
            if (typeName.equals(wrapper.getClass().getName())) {
              wrapperRef.set(wrapper);
              return true;
            }
          }
          return false;
        }
    });
    return new MessageDialogFixture(robot, dialog, wrapperRef.get());
  }

  private MessageDialogFixture(@NotNull Robot robot, @NotNull JDialog target, @NotNull DialogWrapper dialogWrapper) {
    super(robot, target, dialogWrapper);
  }

  @Override
  @NotNull
  public String getMessage() {
    final JTextPane textPane = robot().finder().findByType(target(), JTextPane.class);
    //noinspection ConstantConditions
    return execute(new GuiQuery<String>() {
      @Override
      protected String executeInEDT() throws Throwable {
        return nullToEmpty(textPane.getText());
      }
    });
  }
}
