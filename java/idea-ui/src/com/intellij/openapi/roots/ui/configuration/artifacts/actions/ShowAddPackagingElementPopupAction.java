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
package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementType;

/**
 * @author nik
 */
public class ShowAddPackagingElementPopupAction extends DumbAwareAction {
  private final ArtifactEditorEx myArtifactEditor;

  public ShowAddPackagingElementPopupAction(ArtifactEditorEx artifactEditor) {
    super("Add...");
    myArtifactEditor = artifactEditor;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DefaultActionGroup group = new DefaultActionGroup();
    for (PackagingElementType type : PackagingElementFactory.getInstance().getAllElementTypes()) {
      group.add(new AddNewPackagingElementAction((PackagingElementType<?>)type, myArtifactEditor));
    }
    final DataContext dataContext = e.getDataContext();
    final ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup("Add", group, dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
    popup.showInBestPositionFor(dataContext);
  }
}
