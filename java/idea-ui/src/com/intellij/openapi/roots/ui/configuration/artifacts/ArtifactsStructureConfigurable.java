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
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditorListener;
import com.intellij.openapi.roots.ui.configuration.projectRoot.*;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.ui.MasterDetailsStateService;
import com.intellij.packaging.artifacts.*;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.artifacts.PackagingElementPath;
import com.intellij.packaging.impl.artifacts.PackagingElementProcessor;
import com.intellij.packaging.impl.elements.LibraryElementType;
import com.intellij.packaging.impl.elements.LibraryPackagingElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
@State(
    name = "ArtifactsStructureConfigurable.UI",
    storages = {@Storage(id = "other", file = "$WORKSPACE_FILE$")}
)
public class ArtifactsStructureConfigurable extends BaseStructureConfigurable {
  private ArtifactsStructureConfigurableContextImpl myPackagingEditorContext;
  private ArtifactEditorSettings myDefaultSettings = new ArtifactEditorSettings();

  public ArtifactsStructureConfigurable(@NotNull Project project) {
    super(project);
    MasterDetailsStateService.getInstance(project).register("ArtifactsStructureConfigurable.UI", this);
  }

  public void init(StructureConfigurableContext context, ModuleStructureConfigurable moduleStructureConfigurable,
                   ProjectLibrariesConfigurable projectLibrariesConfig, GlobalLibrariesConfigurable globalLibrariesConfig) {
    super.init(context);
    myPackagingEditorContext = new ArtifactsStructureConfigurableContextImpl(myContext, myProject, myDefaultSettings, new ArtifactAdapter() {
      @Override
      public void artifactAdded(@NotNull Artifact artifact) {
        final MyNode node = addArtifactNode(artifact);
        selectNodeInTree(node);
        myContext.getDaemonAnalyzer().queueUpdate(myPackagingEditorContext.getOrCreateArtifactElement(artifact));
      }
    });

    context.getModulesConfigurator().addAllModuleChangeListener(new ModuleEditor.ChangeListener() {
      public void moduleStateChanged(ModifiableRootModel moduleRootModel) {
        for (ProjectStructureElement element : getProjectStructureElements()) {
          myContext.getDaemonAnalyzer().queueUpdate(element, true, false);
        }
      }
    });

    final ItemsChangeListener listener = new ItemsChangeListener() {
      public void itemChanged(@Nullable Object deletedItem) {
        if (deletedItem instanceof Library || deletedItem instanceof Module) {
          onElementDeleted();
        }
      }

      public void itemsExternallyChanged() {
      }
    };
    moduleStructureConfigurable.addItemsChangeListener(listener);
    projectLibrariesConfig.addItemsChangeListener(listener);
    globalLibrariesConfig.addItemsChangeListener(listener);

    context.addLibraryEditorListener(new LibraryEditorListener() {
      public void libraryRenamed(@NotNull Library library, String oldName, String newName) {
        final Artifact[] artifacts = myPackagingEditorContext.getArtifactModel().getArtifacts();
        for (Artifact artifact : artifacts) {
          updateLibraryElements(artifact, library, oldName, newName);
        }
      }

    });
  }

  private void updateLibraryElements(final Artifact artifact, final Library library, final String oldName, final String newName) {
    if (ArtifactUtil.processPackagingElements(myPackagingEditorContext.getRootElement(artifact), LibraryElementType.LIBRARY_ELEMENT_TYPE,
                                              new PackagingElementProcessor<LibraryPackagingElement>() {
                                                @Override
                                                public boolean process(@NotNull LibraryPackagingElement element,
                                                                       @NotNull PackagingElementPath path) {
                                                  return !isResolvedToLibrary(element, library, oldName);
                                                }
                                              }, myPackagingEditorContext, false, artifact.getArtifactType())) {
      return;
    }
    myPackagingEditorContext.editLayout(artifact, new Runnable() {
      public void run() {
        final ModifiableArtifact modifiableArtifact = myPackagingEditorContext.getOrCreateModifiableArtifactModel().getOrCreateModifiableArtifact(artifact);
        ArtifactUtil.processPackagingElements(modifiableArtifact, LibraryElementType.LIBRARY_ELEMENT_TYPE, new PackagingElementProcessor<LibraryPackagingElement>() {
          @Override
          public boolean process(@NotNull LibraryPackagingElement element, @NotNull PackagingElementPath path) {
            if (isResolvedToLibrary(element, library, oldName)) {
              element.setLibraryName(newName);
            }
            return true;
          }
        }, myPackagingEditorContext, false);
      }
    });
    final ArtifactEditorImpl artifactEditor = myPackagingEditorContext.getArtifactEditor(artifact);
    if (artifactEditor != null) {
      artifactEditor.rebuildTries();
    }
  }

  private static boolean isResolvedToLibrary(LibraryPackagingElement element, Library library, String name) {
    if (!element.getLibraryName().equals(name)) {
      return false;
    }
    
    final LibraryTable table = library.getTable();
    if (table != null) {
      return table.getTableLevel().equals(element.getLevel());
    }
    return element.getLevel().equals(LibraryTableImplUtil.MODULE_LEVEL);
  }

  private void onElementDeleted() {
    for (ArtifactEditorImpl editor : myPackagingEditorContext.getArtifactEditors()) {
      editor.getSourceItemsTree().rebuildTree();
      editor.queueValidation();
    }
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
      elements.add(myPackagingEditorContext.getOrCreateArtifactElement(artifact));
    }
    return elements;
  }

  private MyNode addArtifactNode(final Artifact artifact) {
    final MyNode node = new MyNode(new ArtifactConfigurable(artifact, myPackagingEditorContext, TREE_UPDATER));
    addNode(node, myRoot);
    return node;
  }

  @Override
  protected PersistentStateComponent<?> getAdditionalSettings() {
    return myDefaultSettings;
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
    return myPackagingEditorContext.getOrCreateModifiableArtifactModel();
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
    final String baseName = artifactTemplate.suggestArtifactName();
    String name = baseName;
    int i = 2;
    while (myPackagingEditorContext.getArtifactModel().findArtifact(name) != null) {
      name = baseName + i;
      i++;
    }
    final ModifiableArtifact artifact = myPackagingEditorContext.getOrCreateModifiableArtifactModel().addArtifact(name, type, artifactTemplate.createRootElement(name));
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
    myPackagingEditorContext.getOrCreateModifiableArtifactModel().removeArtifact(artifact);
    myContext.getDaemonAnalyzer().removeElement(myPackagingEditorContext.getOrCreateArtifactElement(artifact));
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
