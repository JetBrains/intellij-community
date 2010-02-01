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
package com.intellij.openapi.application;

import com.intellij.util.messages.Topic;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

public interface UserActivity {

  Topic<UserActivity> TOPIC = Topic.create("UserActivity", UserActivity.class);

  void onKeyboardActivity(KeyEvent e);
  void onMouseActivity(MouseEvent e);

  void onIdle();

  class Adapter implements UserActivity {

    public void onKeyboardActivity(KeyEvent e) {
    }

    public void onMouseActivity(MouseEvent e) {
    }

    public void onIdle() {
    }
  }

  class Util {

    public static boolean isEditorActivity(InputEvent e) {
      Component c = null;
      if (e instanceof KeyEvent) {
        c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      } else if (e instanceof MouseEvent) {
        c = SwingUtilities.getDeepestComponentAt(e.getComponent(), ((MouseEvent)e).getX(), ((MouseEvent)e).getY());
      }

      if (c instanceof JComponent) {
        return Boolean.TRUE.equals(((JComponent)c).getClientProperty("isEditor"));
      }

      return false;
    }
  }

}
