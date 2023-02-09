// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.CommonBundle;
import com.intellij.facet.*;
import com.intellij.facet.impl.invalid.InvalidFacetManager;
import com.intellij.facet.impl.invalid.InvalidFacetType;
import com.intellij.facet.impl.ui.facetType.FacetTypeEditor;
import com.intellij.facet.ui.FacetEditor;
import com.intellij.facet.ui.MultipleFacetSettingsEditor;
import com.intellij.ide.DataManager;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.FacetProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.*;

public class FacetStructureConfigurable extends BaseStructureConfigurable {
  private final Map<FacetType<?, ?>, FacetTypeEditor> myFacetTypeEditors = new HashMap<>();
  private MultipleFacetSettingsEditor myCurrentMultipleSettingsEditor;
  private boolean myTreeWasInitialized;

  public FacetStructureConfigurable(ProjectStructureConfigurable projectStructureConfigurable) {
    super(projectStructureConfigurable);
  }

  @Override
  protected String getComponentStateKey() {
    return "FacetStructureConfigurable.UI";
  }

  /**
   * @deprecated use {@link ProjectStructureConfigurable#getFacetStructureConfigurable()} instead
   */
  @Deprecated(forRemoval = true)
  public static FacetStructureConfigurable getInstance(final @NotNull Project project) {
    return ProjectStructureConfigurable.getInstance(project).getFacetStructureConfigurable();
  }

  public boolean isVisible() {
    return FacetTypeRegistry.getInstance().getFacetTypes().length > 0 || !InvalidFacetManager.getInstance(myProject).getInvalidFacets().isEmpty();
  }

  @Override
  protected void initTree() {
    super.initTree();
    if (!myTreeWasInitialized) {
      myTreeWasInitialized = true;
      final FacetsTreeCellRenderer separatorRenderer = new FacetsTreeCellRenderer();
      final TreeCellRenderer oldRenderer = myTree.getCellRenderer();
      myTree.setCellRenderer(new TreeCellRenderer() {
        @Override
        public Component getTreeCellRendererComponent(JTree tree,
                                                      Object value,
                                                      boolean selected,
                                                      boolean expanded,
                                                      boolean leaf,
                                                      int row,
                                                      boolean hasFocus) {
          if (value instanceof MyNode && ((MyNode)value).getConfigurable() instanceof FrameworkDetectionConfigurable) {
            return separatorRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
          }
          return oldRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        }
      });
      myTree.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          revalidateTree();
        }

        @Override
        public void componentMoved(ComponentEvent e) {
          revalidateTree();
        }

        @Override
        public void componentShown(ComponentEvent e) {
          revalidateTree();
        }
      });
    }
  }

  private void revalidateTree() {
    TreeUtil.invalidateCacheAndRepaint(myTree.getUI());
  }

  @Override
  protected void loadTree() {
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(false);
    boolean hasFacetTypeNodes = false;
    for (FacetType<?,?> facetType : FacetTypeRegistry.getInstance().getFacetTypes()) {
      if (ProjectFacetManager.getInstance(myProject).hasFacets(facetType.getId())) {
        hasFacetTypeNodes = true;
        addFacetTypeNode(facetType);
      }
    }
    if (!InvalidFacetManager.getInstance(myProject).getInvalidFacets().isEmpty()) {
      hasFacetTypeNodes = true;
      addFacetTypeNode(InvalidFacetType.getInstance());
    }
    if (!hasFacetTypeNodes) {
      String title = JavaUiBundle.message("no.facets.are.configured");
      addNode(new MyNode(new TextConfigurable<>(title, title,
                                                JavaUiBundle.message("project.facets.display.name"),
                                                JavaUiBundle.message("text.press.button.to.add.new.facet"),
                                                null)), myRoot);
    }
    addNode(new MyNode(new FrameworkDetectionConfigurable(myProject)), myRoot);
  }

  @Override
  protected Comparator<MyNode> getNodeComparator() {
    return (node1, node2) -> {
      final NamedConfigurable c1 = node1.getConfigurable();
      final NamedConfigurable c2 = node2.getConfigurable();
      if (c1 instanceof FrameworkDetectionConfigurable && !(c2 instanceof FrameworkDetectionConfigurable)) return 1;
      if (!(c1 instanceof FrameworkDetectionConfigurable) && c2 instanceof FrameworkDetectionConfigurable) return -1;

      return StringUtil.naturalCompare(node1.getDisplayName(), node2.getDisplayName());
    };
  }

  private MyNode addFacetTypeNode(FacetType<?, ?> facetType) {
    final MyNode noFrameworksNode = findNodeByObject(myRoot, JavaUiBundle.message("no.facets.are.configured"));
    if (noFrameworksNode != null) {
      removePaths(TreeUtil.getPathFromRoot(noFrameworksNode));
    }

    FacetTypeConfigurable facetTypeConfigurable = new FacetTypeConfigurable(this, facetType);
    MyNode facetTypeNode = new MyNode(facetTypeConfigurable);
    addNode(facetTypeNode, myRoot);

    FacetEditorFacadeImpl editorFacade = myProjectStructureConfigurable.getModulesConfig().getFacetEditorFacade();
    addFacetNodes(facetTypeNode, ProjectFacetManager.getInstance(myProject).getFacets(facetType.getId()), editorFacade);
    return facetTypeNode;
  }

  @NotNull
  @Override
  protected Collection<? extends ProjectStructureElement> getProjectStructureElements() {
    List<ProjectStructureElement> elements = new ArrayList<>();
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      Facet[] facets = FacetManager.getInstance(module).getAllFacets();
      for (Facet facet : facets) {
        elements.add(new FacetProjectStructureElement(myContext, facet));
      }
    }
    return elements;
  }

  public MyNode getOrCreateFacetTypeNode(FacetType facetType) {
    final MyNode node = findNodeByObject(myRoot, facetType);
    if (node != null) {
      return node;
    }
    return addFacetTypeNode(facetType);
  }

  public void addFacetNodes(@NotNull MyNode facetTypeNode, @NotNull List<? extends Facet> facets, @NotNull FacetEditorFacadeImpl editorFacade) {
    for (Facet facet : facets) {
      FacetConfigurable facetConfigurable = editorFacade.getOrCreateConfigurable(facet);
      facetTypeNode.add(new FacetConfigurableNode(facetConfigurable));
      myContext.getDaemonAnalyzer().queueUpdate(new FacetProjectStructureElement(myContext, facet));
    }
    sortDescendants(facetTypeNode);
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

  @Override
  public void reset() {
    myFacetTypeEditors.clear();
    super.reset();
    TreeUtil.expandAll(myTree);
  }


  @Override
  public void apply() throws ConfigurationException {
    super.apply();
    for (FacetTypeEditor editor : myFacetTypeEditors.values()) {
      editor.apply();
    }
  }

  @Override
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

  @Override
  public void disposeUIResources() {
    super.disposeUIResources();

    for (FacetTypeEditor editor : myFacetTypeEditors.values()) {
      editor.disposeUIResources();
    }
    myFacetTypeEditors.clear();
  }

  @Override
  @NotNull
  protected ArrayList<AnAction> createActions(final boolean fromPopup) {
    ArrayList<AnAction> actions = new ArrayList<>();
    actions.add(new AbstractAddGroup(CommonBundle.message("button.add")) {
      @Override
      public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        return AddFacetOfTypeAction.createAddFacetActions(FacetStructureConfigurable.this);
      }
    });
    if (fromPopup) {
      actions.add(new MyNavigateAction());
    }
    actions.add(new MyRemoveAction());
    if (fromPopup) {
      actions.add(Separator.getInstance());
      addCollapseExpandActions(actions);
    }
    return actions;
  }

  @Override
  protected boolean updateMultiSelection(final List<? extends NamedConfigurable> selectedConfigurables) {
    return updateMultiSelection(selectedConfigurables, getDetailsComponent());
  }

  public boolean updateMultiSelection(final List<? extends NamedConfigurable> selectedConfigurables, final DetailsComponent detailsComponent) {
    FacetType selectedFacetType = null;
    List<FacetEditor> facetEditors = new ArrayList<>();
    for (NamedConfigurable selectedConfigurable : selectedConfigurables) {
      if (selectedConfigurable instanceof FacetConfigurable facetConfigurable) {
        FacetType facetType = facetConfigurable.getEditableObject().getType();
        if (selectedFacetType != null && selectedFacetType != facetType) {
          return false;
        }
        selectedFacetType = facetType;
        facetEditors.add(facetConfigurable.getEditor());
      }
    }
    if (facetEditors.size() <= 1) {
      return false;
    }

    FacetEditor[] selectedEditors = facetEditors.toArray(new FacetEditor[0]);
    MultipleFacetSettingsEditor editor = selectedFacetType.createMultipleConfigurationsEditor(myProject, selectedEditors);
    if (editor == null) {
      return false;
    }

    setSelectedNode(null);
    myCurrentMultipleSettingsEditor = editor;
    detailsComponent.setText(JavaUiBundle.message("multiple.facets.banner.0.1.facets", selectedEditors.length,
                                                        selectedFacetType.getPresentableName()));
    detailsComponent.setContent(editor.createComponent());
    return true;
  }

  @Override
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

  @Override
  @Nullable
  protected AbstractAddGroup createAddAction() {
    return null;
  }

  @Override
  protected List<? extends RemoveConfigurableHandler<?>> getRemoveHandlers() {
    return Collections.singletonList(new FacetRemoveHandler());
  }

  @Override
  public String getDisplayName() {
    return JavaUiBundle.message("project.facets.display.name");
  }

  @Override
  public String getHelpTopic() {
    final Component component = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(DataManager.getInstance().getDataContext());
    if (myTree.equals(component)) {
      final NamedConfigurable selectedConfigurable = getSelectedConfigurable();
      if (selectedConfigurable instanceof FacetTypeConfigurable) {
        final FacetType facetType = ((FacetTypeConfigurable)selectedConfigurable).getEditableObject();
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

  @Override
  @NotNull
  public String getId() {
    return "project.facets";
  }

  @Override
  public void dispose() {
  }

  private class FacetRemoveHandler extends RemoveConfigurableHandler<Facet> {
    FacetRemoveHandler() {
      super(FacetConfigurable.class);
    }

    @Override
    public boolean remove(@NotNull Collection<? extends Facet> facets) {
      for (Facet facet : facets) {
        List<Facet> removed = myContext.myModulesConfigurator.getFacetsConfigurator().removeFacet(facet);
        myProjectStructureConfigurable.getModulesConfig().removeFacetNodes(removed);
        for (Facet removedFacet : removed) {
          myContext.getDaemonAnalyzer().removeElement(new FacetProjectStructureElement(myContext, removedFacet));
        }
      }
      return true;
    }
  }

  private class FacetConfigurableNode extends MyNode {
    FacetConfigurableNode(final FacetConfigurable facetConfigurable) {
      super(facetConfigurable);
    }

    @Override
    @NotNull
    public String getDisplayName() {
      FacetConfigurable facetConfigurable = (FacetConfigurable)getConfigurable();
      String moduleName = myContext.getRealName(facetConfigurable.getEditableObject().getModule());
      return facetConfigurable.getDisplayName() + " (" + moduleName + ")";
    }
  }

  private final class MyNavigateAction extends AnAction implements DumbAware {
    private MyNavigateAction() {
      super(JavaUiBundle.message("action.name.facet.navigate"));
      registerCustomShortcutSet(CommonShortcuts.getEditSource(), myTree);
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      NamedConfigurable selected = getSelectedConfigurable();
      e.getPresentation().setEnabled(selected instanceof FacetConfigurable);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
      NamedConfigurable selected = getSelectedConfigurable();
      if (selected instanceof FacetConfigurable) {
        myProjectStructureConfigurable.select(((FacetConfigurable)selected).getEditableObject(), true);
      }
    }
  }
}