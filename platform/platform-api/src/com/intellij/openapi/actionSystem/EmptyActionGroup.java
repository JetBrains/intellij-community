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
package com.intellij.openapi.actionSystem;

/**
 * This class purpose is to override (hide) already registered action groups by means of plugin.xml.
 *
 * @see EmptyAction
 *
 * @author gregsh
 */
public final class EmptyActionGroup extends DefaultActionGroup {
  public EmptyActionGroup() {
    getTemplatePresentation().setEnabledAndVisible(false);
  }

  @Override
  public void update(AnActionEvent e) {
    getTemplatePresentation().setEnabledAndVisible(false);
  }
}
