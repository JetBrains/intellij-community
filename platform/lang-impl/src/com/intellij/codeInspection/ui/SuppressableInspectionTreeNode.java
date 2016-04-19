/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public abstract class SuppressableInspectionTreeNode extends CachedInspectionTreeNode {
  private final static Logger LOG = Logger.getInstance(SuppressableInspectionTreeNode.class);
  private final InspectionResultsView myView;

  protected SuppressableInspectionTreeNode(Object userObject, InspectionToolPresentation presentation) {
    super(userObject);
    myView = presentation.getContext().getView();
  }

  public boolean canSuppress() {
    return isLeaf();
  }

  public final boolean isAlreadySuppressedFromView() {
    final Object usrObj = getUserObject();
    LOG.assertTrue(usrObj != null);
    return myView.getSuppressedNodes().contains(usrObj);
  }

  public final void markAsSuppressedFromView() {
    final Object usrObj = getUserObject();
    LOG.assertTrue(usrObj != null);
    myView.getSuppressedNodes().add(usrObj);
  }

  @Nullable
  @Override
  public String getCustomizedTailText() {
    final String text = super.getCustomizedTailText();
    if (text != null) {
      return text;
    }
    return isAlreadySuppressedFromView() ? "Suppressed" : null;
  }
}
