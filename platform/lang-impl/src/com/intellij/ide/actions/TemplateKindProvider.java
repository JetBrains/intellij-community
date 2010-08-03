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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiDirectory;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public abstract class TemplateKindProvider {

  private final static ExtensionPointName<TemplateKindProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.javaee.templateKindProvider");

  public static void addAdditionalKinds(AnAction action, PsiDirectory dir, CreateFileFromTemplateDialog.Builder builder) {
    String id = ActionManager.getInstance().getId(action);
    for (TemplateKindProvider provider : Extensions.getExtensions(EP_NAME)) {
      for (Kind kind : provider.getAdditionalKinds(dir)) {
        builder.addKind(kind.name, kind.icon, kind.templateName);
      }
    }
  }
  public abstract boolean isAvailable(Class<? extends AnAction> actionClass);
  public abstract Kind[] getAdditionalKinds(PsiDirectory dir);

  public static class Kind {
    public final String name;
    public final String templateName;
    public final Icon icon;

    public Kind(String name, String templateName, Icon icon) {
      this.name = name;
      this.templateName = templateName;
      this.icon = icon;
    }
  }
}
