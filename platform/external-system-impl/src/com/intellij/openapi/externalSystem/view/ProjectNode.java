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
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 10/15/2014
 */
public class ProjectNode extends ExternalSystemNode<ProjectData> {
  private String myTooltipCache;
  private boolean singleModuleProject = false;

  public ProjectNode(ExternalProjectsView externalProjectsView, DataNode<ProjectData> projectDataNode) {
    super(externalProjectsView, null, projectDataNode);
    updateProject();
  }

  @Override
  protected void update(PresentationData presentation) {
    super.update(presentation);
    presentation.setIcon(getUiAware().getProjectIcon());
  }

  public ExternalSystemNode getGroup() {
    return (ExternalSystemNode)getParent();
  }

  @NotNull
  @Override
  protected List<? extends ExternalSystemNode> doBuildChildren() {
    final List<? extends ExternalSystemNode> children = super.doBuildChildren();
    final List<ExternalSystemNode> visibleChildren = ContainerUtil.filter(children, new Condition<ExternalSystemNode>() {
      @Override
      public boolean value(ExternalSystemNode node) {
        return node.isVisible();
      }
    });
    if (visibleChildren.size() == 1 && visibleChildren.get(0).getName().equals(getName())) {
      singleModuleProject = true;
      //noinspection unchecked
      return visibleChildren.get(0).doBuildChildren();
    }
    else {
      singleModuleProject = false;
      return visibleChildren;
    }
  }

  public boolean isSingleModuleProject() {
    getChildren();
    return singleModuleProject;
  }

  void updateProject() {
    myTooltipCache = makeDescription();
    getStructure().updateFrom(getParent());
  }

  @Override
  public String getName() {
    final ProjectData projectData = getData();
    return projectData != null ? projectData.getExternalName() : "unspecified";
  }

  @Override
  protected void doUpdate() {
    String autoImportHint = null;
    final ProjectData projectData = getData();
    if (projectData != null) {
      final AbstractExternalSystemSettings externalSystemSettings =
        ExternalSystemApiUtil.getSettings(getExternalProjectsView().getProject(), getData().getOwner());
      final ExternalProjectSettings projectSettings =
        externalSystemSettings.getLinkedProjectSettings(projectData.getLinkedExternalProjectPath());
      if (projectSettings != null && projectSettings.isUseAutoImport()) autoImportHint = "auto-import enabled";
    }

    setNameAndTooltip(getName(), myTooltipCache, autoImportHint);
  }

  @Override
  protected SimpleTextAttributes getPlainAttributes() {
    return super.getPlainAttributes();
  }

  private String makeDescription() {
    StringBuilder desc = new StringBuilder();
    final ProjectData projectData = getData();
    desc
      .append("<table>" +
              "<tr>" +
              "<td nowrap>" +
              "<table>" +
              "<tr><td nowrap>Project:</td><td nowrap>").append(getName()).append("</td></tr>")
      .append(projectData != null ?
              "<tr><td nowrap>Location:</td><td nowrap>" + projectData.getLinkedExternalProjectPath() + "</td></tr>" : "")
      .append(projectData != null && !StringUtil.isEmptyOrSpaces(projectData.getDescription()) ?
              "<tr><td colspan='2' nowrap><hr align='center' width='90%' />" + projectData.getDescription() + "</td></tr>" : "")
      .append("</td></tr>" +
              "</table>" +
              "</td>" +
              "</tr>");
    appendProblems(desc);
    desc.append("</table>");
    return desc.toString();
  }

  private void appendProblems(StringBuilder desc) {
    // TBD
  }

  @Override
  protected void setNameAndTooltip(String name, @Nullable String tooltip, SimpleTextAttributes attributes) {
    super.setNameAndTooltip(name, tooltip, attributes);
  }

  @Override
  @Nullable
  @NonNls
  protected String getMenuId() {
    return "ExternalSystemView.ProjectMenu";
  }
}
