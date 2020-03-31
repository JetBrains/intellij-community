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
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewBundle;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class FieldsFilter implements Filter{
  @NonNls public static final String ID = "SHOW_FIELDS";

  @Override
  public boolean isVisible(TreeElement treeNode) {
    return !(treeNode instanceof PsiFieldTreeElement);
  }

  @Override
  @NotNull
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(StructureViewBundle.message("action.structureview.show.fields"), null, PlatformIcons.FIELD_ICON);
  }

  @Override
  @NotNull
  public String getName() {
    return ID;
  }

  @Override
  public boolean isReverted() {
    return true;
  }
}
