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
package com.intellij.ui;

import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author gregsh
 */
public abstract class DumbAwareActionButton extends AnActionButton implements DumbAware {
  public DumbAwareActionButton(String text) {
    super(text);
  }

  public DumbAwareActionButton(String text, String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  public DumbAwareActionButton(String text, Icon icon) {
    super(text, icon);
  }

  public DumbAwareActionButton() {
  }
}
