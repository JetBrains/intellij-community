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
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.projectRoot.BaseStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.packaging.artifacts.*;
import com.intellij.packaging.elements.CompositePackagingElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author nik
 */
@State(
    name = "ArtifactsStructureConfigurable.UI",
    storages = {@Storage(id = "other", file = "$WORKSPACE_FILE$")}
)
public class ArtifactsStructureConfigurable extends BaseStructureConfigurable {
  @NonNls private static final String DEFAULT_ARTIFACT_NAME = "unnamed";
  private ArtifactsStructureConfigurableContextImpl myPackagingEditorContext;

  public ArtifactsStructureConfigurable(@NotNull Project project) {
    super(project);
  }

  @Override
  public void init(StructureConfigurableContext context) {
    super.init(context);
    myPackagingEditorContext = new ArtifactsStructureConfigurableContextImpl(myContext, myProject, new ArtifactAdapter() {
      @Override
      public void artifactAdded(@NotNull Artifact artifact) {
        final MyNode node = addArtifactNode(artifact);
        selectNodeInTree(node);
        myContext.getDaemonAnalyzer().queueUpdate(new ArtifactProjectStructureElement(myContext, myPackagingEditorContext, artifact));
      }
    });
  }

  @Nls
  public String getDisplayName() {
    return ProjectBundle.message("display.name.artifacts");
  }

  protected void loadTree() {
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(false);
    for (Artifact artifact : myPackagingEditorContext.getArtifactModel().getArtifacts()) {
      addArtifactNode(artifact);
    }
  }

  @NotNull
  @Override
  protected Collection<? extends ProjectStructureElement> getProjectStructureElements() {
    final List<ProjectStructureElement> elements = new ArrayList<ProjectStructureElement>();
    for (Artifact artifact : myPackagingEditorContext.getArtifactModel().getArtifacts()) {
      elements.add(new ArtifactProjectStructureElement(myContext, myPackagingEditorContext, artifact));
    }
    return elements;
  }

  private MyNode addArtifactNode(final Artifact artifact) {
    final MyNode node = new MyNode(new ArtifactConfigurable(artifact, myPackagingEditorContext, TREE_UPDATER, myContext));
    addNode(node, myRoot);
    return node;
  }

  @Override
  public void reset() {
    myPackagingEditorContext.resetModifiableModel();
    super.reset();
  }

  @Override
  public boolean isModified() {
    final ModifiableArtifactModel modifiableModel = myPackagingEditorContext.getActualModifiableModel();
    if (modifiableModel != null && modifiableModel.isModified()) {
      return true;
    }
    return myPackagingEditorContext.getManifestFilesInfo().isManifestFilesModified() || super.isModified();
  }

  public ArtifactsStructureConfigurableContext getArtifactsStructureContext() {
    return myPackagingEditorContext;
  }

  public ModifiableArtifactModel getModifiableArtifactModel() {
    return myPackagingEditorContext.getModifiableArtifactModel();
  }

  protected AbstractAddGroup createAddAction() {
    return new AbstractAddGroup(ProjectBundle.message("add.new.header.text")) {
      @NotNull
      @Override
      public AnAction[] getChildren(@Nullable AnActionEvent e) {
        final ArtifactType[] types = ArtifactType.getAllTypes();
        final AnAction[] actions = new AnAction[types.length];
        for (int i = 0; i < types.length; i++) {
          actions[i] = createAddArtifactAction(types[i]);
        }
        return actions;
      }
    };
  }

  private AnAction createAddArtifactAction(@NotNull final ArtifactType type) {
    final List<? extends ArtifactTemplate> templates = type.getNewArtifactTemplates(myPackagingEditorContext);
    final ArtifactTemplate emptyTemplate = new ArtifactTemplate() {
      @Override
      public String getPresentableName() {
        return "Empty";
      }

      @Override
      public CompositePackagingElement<?> createRootElement(@NotNull String artifactName) {
        return type.createRootElement(artifactName);
      }

    };

    if (templates.isEmpty()) {
      return new AddArtifactAction(type, emptyTemplate, type.getPresentableName(), type.getIcon());
    }
    final DefaultActionGroup group = new DefaultActionGroup(type.getPresentableName(), true);
    group.getTemplatePresentation().setIcon(type.getIcon());
    group.add(new AddArtifactAction(type, emptyTemplate, emptyTemplate.getPresentableName(), null));
    group.addSeparator();
    for (ArtifactTemplate template : templates) {
      group.add(new AddArtifactAction(type, template, template.getPresentableName(), null));
    }
    return group;
  }

  private void addArtifact(@NotNull ArtifactType type, @NotNull ArtifactTemplate artifactTemplate) {
    String name = DEFAULT_ARTIFACT_NAME;
    int i = 2;
    while (myPackagingEditorContext.getArtifactModel().findArtifact(name) != null) {
      name = DEFAULT_ARTIFACT_NAME + i;
      i++;
    }
    final ModifiableArtifact artifact = myPackagingEditorContext.getModifiableArtifactModel().addArtifact(name, type, artifactTemplate.createRootElement(name));
    selectNodeInTree(findNodeByObject(myRoot, artifact));
  }

  @Override
  public void apply() throws ConfigurationException {
    myPackagingEditorContext.saveEditorSettings();
    super.apply();

    myPackagingEditorContext.getManifestFilesInfo().saveManifestFiles();
    final ModifiableArtifactModel modifiableModel = myPackagingEditorContext.getActualModifiableModel();
    if (modifiableModel != null) {
      new WriteAction() {
        protected void run(final Result result) {
          modifiableModel.commit();
        }
      }.execute();
      myPackagingEditorContext.resetModifiableModel();
    }

    reset(); // TODO: fix to not reset on apply!
  }

  @Override
  public void disposeUIResources() {
    myPackagingEditorContext.saveEditorSettings();
    super.disposeUIResources();
    myPackagingEditorContext.disposeUIResources();
  }

  @Override
  public String getHelpTopic() {
    final String topic = super.getHelpTopic();
    return topic != null ? topic : "reference.settingsdialog.project.structure.artifacts";
  }

  @Override
  protected void removeArtifact(Artifact artifact) {
    myPackagingEditorContext.getModifiableArtifactModel().removeArtifact(artifact);
    myContext.getDaemonAnalyzer().removeElement(new ArtifactProjectStructureElement(myContext, myPackagingEditorContext, artifact));
  }

  protected void processRemovedItems() {
  }

  protected boolean wasObjectStored(Object editableObject) {
    return false;
  }

  public String getId() {
    return "project.artifacts";
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  public void dispose() {
  }

  public Icon getIcon() {
    return null;
  }


  private class AddArtifactAction extends DumbAwareAction {
    private final ArtifactType myType;
    private final ArtifactTemplate myArtifactTemplate;

    public AddArtifactAction(@NotNull ArtifactType type, @NotNull ArtifactTemplate artifactTemplate, final @NotNull String actionText,
                             final Icon icon) {
      super(actionText, null, icon);
      myType = type;
      myArtifactTemplate = artifactTemplate;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      addArtifact(myType, myArtifactTemplate);
    }
  }
}
