/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.openapi.options.binding;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public abstract class BindableConfigurable implements UnnamedConfigurable {

  protected ControlBinder myBinder;

  protected BindableConfigurable(ControlBinder binder) {
    myBinder = binder;
  }
  
  protected void bindAnnotations() {
    myBinder.bindAnnotations(this);
  }

  protected void bindControl(JComponent control, String propertyName, boolean instant) {
    myBinder.bindControl(control, propertyName, instant);
  }

  protected void bindControl(ControlValueAccessor controlAccessor, String propertyName, boolean instant) {
    myBinder.bindControl(controlAccessor, propertyName, instant);
  }

  public boolean isModified() {
    return myBinder.isModified();
  }

  public void apply() throws ConfigurationException {
    myBinder.apply();
  }

  public void reset() {
    myBinder.reset();
  }
}
                                        