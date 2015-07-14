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
package com.intellij.openapi.externalSystem.view;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 11/7/2014
 */
@Order(1)
public class ModuleNode extends ExternalSystemNode<ModuleData> {
  private final boolean myIsRoot;
  private final ModuleData myData;
  private final RunConfigurationsNode myRunConfigurationsNode;

  public ModuleNode(ExternalProjectsView externalProjectsView,
                    DataNode<ModuleData> dataNode,
                    boolean isRoot) {
    super(externalProjectsView, null, dataNode);
    myIsRoot = isRoot;
    myData = dataNode.getData();
    myRunConfigurationsNode = new RunConfigurationsNode(externalProjectsView, this);
  }

  @Override
  protected void update(PresentationData presentation) {
    super.update(presentation);
    presentation.setIcon(getUiAware().getProjectIcon());

    String hint = null;
    if (myIsRoot) {
      hint = "root";
    }

    final String tooltip = myData.toString() + (myData.getDescription() != null ? "<br>" + myData.getDescription() : "");
    setNameAndTooltip(getName(), tooltip, hint);
  }

  @NotNull
  @Override
  protected List<? extends ExternalSystemNode> doBuildChildren() {
    List<ExternalSystemNode<?>> myChildNodes = ContainerUtil.newArrayList();
    //noinspection unchecked
    myChildNodes.addAll((Collection<? extends ExternalSystemNode<?>>)super.doBuildChildren());
    myChildNodes.add(myRunConfigurationsNode);
    return myChildNodes;
  }

  @Override
  public String getName() {
    return myData.getId();
  }

  @Nullable
  @Override
  protected String getMenuId() {
    return "ExternalSystemView.ModuleMenu";
  }

  @Override
  public boolean isVisible() {
    return super.isVisible();
  }

  @Override
  public int compareTo(@NotNull ExternalSystemNode node) {
    return myIsRoot ? -1 : (node instanceof ModuleNode && ((ModuleNode)node).myIsRoot) ? 1 : super.compareTo(node);
  }

  public void updateRunConfigurations() {
    myRunConfigurationsNode.updateRunConfigurations();
    childrenChanged();
    getExternalProjectsView().updateUpTo(this);
    getExternalProjectsView().updateUpTo(myRunConfigurationsNode);
  }
}
