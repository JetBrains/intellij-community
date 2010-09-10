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
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.facet.impl.autodetecting.FacetAutodetectingManager;
import com.intellij.facet.impl.autodetecting.FacetAutodetectingManagerImpl;
import com.intellij.facet.impl.ui.facetType.FacetTypeEditor;
import com.intellij.facet.ui.FacetEditor;
import com.intellij.facet.ui.MultipleFacetSettingsEditor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author nik
 */
@State(
    name = "FacetStructureConfigurable.UI",
    storages = {
      @Storage(
          id = "other",
          file = "$WORKSPACE_FILE$"
      )
    }
)
public class FacetStructureConfigurable extends BaseStructureConfigurable {
  private static final Icon ICON = IconLoader.getIcon("/modules/modules.png");//todo[nik] use facets icon
  private final ModuleManager myModuleManager;
  private final Map<FacetType<?, ?>, FacetTypeEditor> myFacetTypeEditors = new HashMap<FacetType<?,?>, FacetTypeEditor>();
  private MultipleFacetSettingsEditor myCurrentMultipleSettingsEditor;

  public FacetStructureConfigurable(final Project project, ModuleManager moduleManager) {
    super(project);
    myModuleManager = moduleManager;
  }

  public static FacetStructureConfigurable getInstance(final @NotNull Project project) {
    return ServiceManager.getService(project, FacetStructureConfigurable.class);
  }

  public static boolean isEnabled() {
    return FacetTypeRegistry.getInstance().getFacetTypes().length > 0;
  }

  protected void loadTree() {
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    for (FacetType<?,?> facetType : FacetTypeRegistry.getInstance().getFacetTypes()) {
      FacetTypeConfigurable facetTypeConfigurable = new FacetTypeConfigurable(this, facetType);
      MyNode facetTypeNode = new MyNode(facetTypeConfigurable);
      addNode(facetTypeNode, myRoot);

      for (Module module : myModuleManager.getModules()) {
        Collection<? extends Facet> facets = FacetManager.getInstance(module).getFacetsByType(facetType.getId());
        for (Facet facet : facets) {
          FacetEditorFacadeImpl editorFacade = ModuleStructureConfigurable.getInstance(myProject).getFacetEditorFacade();
          FacetConfigurable facetConfigurable = editorFacade.getOrCreateConfigurable(facet);
          addNode(new FacetConfigurableNode(facetConfigurable), facetTypeNode);
        }
      }
    }
  }

  @Nullable
  public FacetTypeEditor getFacetTypeEditor(@NotNull FacetType<?, ?> facetType) {
    return myFacetTypeEditors.get(facetType);
  }

  public FacetTypeEditor getOrCreateFacetTypeEditor(@NotNull FacetType<?, ?> facetType) {
    FacetTypeEditor editor = myFacetTypeEditors.get(facetType);
    if (editor == null) {
      editor = new FacetTypeEditor(myProject, myContext, facetType);
      editor.reset();
      myFacetTypeEditors.put(facetType, editor);
    }
    return editor;
  }

  public void reset() {
    super.reset();
    myFacetTypeEditors.clear();
  }


  public void apply() throws ConfigurationException {
    super.apply();
    for (FacetTypeEditor editor : myFacetTypeEditors.values()) {
      editor.apply();
    }
    if (!myProject.isDefault()) {
      ((FacetAutodetectingManagerImpl)FacetAutodetectingManager.getInstance(myProject)).redetectFacets();
    }
  }

  public boolean isModified() {
    return super.isModified() || isEditorsModified();
  }

  private boolean isEditorsModified() {
    for (FacetTypeEditor editor : myFacetTypeEditors.values()) {
      if (editor.isModified()) {
        return true;
      }
    }
    return false;
  }

  public void disposeUIResources() {
    super.disposeUIResources();

    for (FacetTypeEditor editor : myFacetTypeEditors.values()) {
      editor.disposeUIResources();
    }
    myFacetTypeEditors.clear();
  }

  @NotNull
  protected ArrayList<AnAction> createActions(final boolean fromPopup) {
    ArrayList<AnAction> actions = new ArrayList<AnAction>();
    if (fromPopup) {
      actions.add(new MyNavigateAction());
    }
    actions.add(new MyRemoveAction());
    actions.add(Separator.getInstance());
    addCollapseExpandActions(actions);
    return actions;
  }

  protected List<Facet> removeFacet(final Facet facet) {
    List<Facet> removed = super.removeFacet(facet);
    ModuleStructureConfigurable.getInstance(myProject).removeFacetNodes(removed);
    return removed;
  }

  protected boolean updateMultiSelection(final List<NamedConfigurable> selectedConfigurables) {
    return updateMultiSelection(selectedConfigurables, getDetailsComponent());
  }

  public boolean updateMultiSelection(final List<NamedConfigurable> selectedConfigurables, final DetailsComponent detailsComponent) {
    FacetType selectedFacetType = null;
    List<FacetEditor> facetEditors = new ArrayList<FacetEditor>();
    for (NamedConfigurable selectedConfigurable : selectedConfigurables) {
      if (selectedConfigurable instanceof FacetConfigurable) {
        FacetConfigurable facetConfigurable = (FacetConfigurable)selectedConfigurable;
        FacetType facetType = facetConfigurable.getEditableObject().getType();
        if (selectedFacetType != null && selectedFacetType != facetType) {
          return false;
        }
        selectedFacetType = facetType;
        facetEditors.add(facetConfigurable.getEditor());
      }
    }
    if (facetEditors.size() <= 1 || selectedFacetType == null) {
      return false;
    }

    FacetEditor[] selectedEditors = facetEditors.toArray(new FacetEditor[facetEditors.size()]);
    MultipleFacetSettingsEditor editor = selectedFacetType.createMultipleConfigurationsEditor(myProject, selectedEditors);
    if (editor == null) {
      return false;
    }

    setSelectedNode(null);
    myCurrentMultipleSettingsEditor = editor;
    detailsComponent.setText(ProjectBundle.message("multiple.facets.banner.0.1.facets", selectedEditors.length,
                                                        selectedFacetType.getPresentableName()));
    detailsComponent.setContent(editor.createComponent());
    return true;
  }

  protected void updateSelection(@Nullable final NamedConfigurable configurable) {
    disposeMultipleSettingsEditor();
    if (configurable instanceof FacetTypeConfigurable) {
      ((FacetTypeConfigurable)configurable).updateComponent();
    }
    super.updateSelection(configurable);
  }

  public void disposeMultipleSettingsEditor() {
    if (myCurrentMultipleSettingsEditor != null) {
      myCurrentMultipleSettingsEditor.disposeUIResources();
      myCurrentMultipleSettingsEditor = null;
    }
  }

  @Nullable
  protected AbstractAddGroup createAddAction() {
    return null;
  }

  protected void processRemovedItems() {
  }

  protected boolean wasObjectStored(final Object editableObject) {
    return false;
  }

  public String getDisplayName() {
    return ProjectBundle.message("project.facets.display.name");
  }

  public Icon getIcon() {
    return ICON;
  }

  public String getHelpTopic() {
    final Component component = DataKeys.CONTEXT_COMPONENT.getData(DataManager.getInstance().getDataContext());
    if (myTree.equals(component)) {
      final NamedConfigurable selectedConfugurable = getSelectedConfugurable();
      if (selectedConfugurable instanceof FacetTypeConfigurable) {
        final FacetType facetType = ((FacetTypeConfigurable)selectedConfugurable).getEditableObject();
        final String topic = facetType.getHelpTopic();
        if (topic != null) {
          return topic;
        }
      }
    }
    if (myCurrentMultipleSettingsEditor != null) {
      final String topic = myCurrentMultipleSettingsEditor.getHelpTopic();
      if (topic != null) {
        return topic;
      }
    }
    String topic = super.getHelpTopic();
    if (topic != null) {
      return topic;
    }
    return "reference.settingsdialog.project.structure.facet";
  }

  public String getId() {
    return "project.facets";
  }

  public Runnable enableSearch(final String option) {
    return null;
  }

  public void dispose() {
  }

  private class FacetConfigurableNode extends MyNode {
    public FacetConfigurableNode(final FacetConfigurable facetConfigurable) {
      super(facetConfigurable);
    }

    @NotNull
    public String getDisplayName() {
      FacetConfigurable facetConfigurable = (FacetConfigurable)getConfigurable();
      String moduleName = myContext.getRealName(facetConfigurable.getEditableObject().getModule());
      return facetConfigurable.getDisplayName() + " (" + moduleName + ")";
    }
  }

  private class MyNavigateAction extends AnAction implements DumbAware {
    private MyNavigateAction() {
      super(ProjectBundle.message("action.name.facet.navigate"));
      registerCustomShortcutSet(CommonShortcuts.getEditSource(), myTree);
    }

    public void update(final AnActionEvent e) {
      NamedConfigurable selected = getSelectedConfugurable();
      e.getPresentation().setEnabled(selected instanceof FacetConfigurable);
    }

    public void actionPerformed(final AnActionEvent e) {
      NamedConfigurable selected = getSelectedConfugurable();
      if (selected instanceof FacetConfigurable) {
        ProjectStructureConfigurable.getInstance(myProject).select(((FacetConfigurable)selected).getEditableObject(), true);
      }
    }
  }
}
