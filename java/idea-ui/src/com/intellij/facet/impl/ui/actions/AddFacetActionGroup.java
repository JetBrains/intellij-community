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

package com.intellij.facet.impl.ui.actions;

import com.intellij.facet.impl.ui.FacetEditorFacade;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;

/**
 * @author nik
*/
public class AddFacetActionGroup extends ActionGroup implements DumbAware {
  private final boolean myFromPopup;
  private final FacetEditorFacade myEditor;
  private AnAction[] myCachedChildren;

  public AddFacetActionGroup(final String groupName, final boolean fromPopup, final FacetEditorFacade editor) {
    super(groupName, true);
    myFromPopup = fromPopup;
    myEditor = editor;
  }

  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (myCachedChildren == null) {
      final FacetType[] types = FacetTypeRegistry.getInstance().getFacetTypes();
      Arrays.sort(types, new Comparator<FacetType>() {
        public int compare(final FacetType o1, final FacetType o2) {
          return o1.getPresentableName().compareTo(o2.getPresentableName());
        }
      });
      myCachedChildren = new AnAction[types.length];
      for (int i = 0; i < types.length; i++) {
        myCachedChildren[i] = new AddFacetAction(myEditor, types[i]);
      }
    }

    return myCachedChildren;
  }

  public void update(AnActionEvent e) {
    final boolean visible = isVisible();
    if (myFromPopup) {
      e.getPresentation().setVisible(visible);
    }
    else {
      e.getPresentation().setEnabled(visible);
    }
  }

  private boolean isVisible() {
    for (FacetType type : FacetTypeRegistry.getInstance().getFacetTypes()) {
      if (AddFacetAction.isVisible(myEditor, type)) {
        return true;
      }
    }
    return false;
  }
}
