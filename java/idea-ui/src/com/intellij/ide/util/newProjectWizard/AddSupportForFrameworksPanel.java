/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.ide.util.newProjectWizard;

import com.intellij.CommonBundle;
import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings;
import com.intellij.facet.impl.ui.libraries.LibraryOptionsPanel;
import com.intellij.facet.ui.FacetBasedFrameworkSupportProvider;
import com.intellij.framework.FrameworkGroup;
import com.intellij.framework.FrameworkVersion;
import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.framework.addSupport.FrameworkVersionListener;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportUtil;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportCommunicator;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.IdeaModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author nik
 */
public class AddSupportForFrameworksPanel implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.newProjectWizard.AddSupportForFrameworksStep");
  @NonNls private static final String EMPTY_CARD = "empty";
  private JPanel myMainPanel;
  private JPanel myFrameworksPanel;
  private JLabel myLabel;

  private List<FrameworkSupportInModuleProvider> myProviders;
  private List<FrameworkSupportNodeBase> myRoots;

  private final LibrariesContainer myLibrariesContainer;
  private final FrameworkSupportModelBase myModel;
  private final JPanel myOptionsPanel;
  private final FrameworksTree myFrameworksTree;
  private final Map<FrameworkSupportNode, FrameworkSupportOptionsComponent> myInitializedOptionsComponents = new HashMap<>();
  private final Map<FrameworkGroup<?>, JPanel> myInitializedGroupPanels = new HashMap<>();
  private FrameworkSupportNodeBase myLastSelectedNode;

  private Collection<FrameworkSupportNodeBase> myAssociatedFrameworks;
  @Nullable
  private final JPanel myAssociatedFrameworksPanel;

  public AddSupportForFrameworksPanel(final List<FrameworkSupportInModuleProvider> providers,
                                      final FrameworkSupportModelBase model, boolean vertical, @Nullable JPanel associatedFrameworksPanel) {
    myModel = model;
    myAssociatedFrameworksPanel = associatedFrameworksPanel;
    myLibrariesContainer = model.getLibrariesContainer();

    myLabel.setVisible(!vertical);
    Splitter splitter = vertical ? new Splitter(true, 0.6f, 0.2f, 0.8f) : new Splitter(false, 0.3f, 0.3f, 0.7f);
    splitter.setHonorComponentsMinimumSize(false);
    myFrameworksTree = new FrameworksTree(model) {
      @Override
      protected void onNodeStateChanged(CheckedTreeNode node) {
        if (!(node instanceof FrameworkSupportNode)) return;

        final FrameworkSupportNode frameworkSupportNode = (FrameworkSupportNode)node;
        if (frameworkSupportNode == getSelectedNode()) {
          updateOptionsPanel();
        }
        final FrameworkSupportInModuleConfigurable configurable = frameworkSupportNode.getConfigurable();
        configurable.onFrameworkSelectionChanged(node.isChecked());
        myModel.onFrameworkSelectionChanged(frameworkSupportNode);
        onFrameworkStateChanged();
      }
    };
    model.addFrameworkVersionListener(new FrameworkVersionListener() {
      @Override
      public void versionChanged(FrameworkVersion version) {
        ((DefaultTreeModel)myFrameworksTree.getModel()).nodeChanged(getSelectedNode());
      }
    }, this);

    myFrameworksTree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        onSelectionChanged();
      }
    });

    JPanel treePanel = new JPanel(new BorderLayout());
    treePanel.add(ScrollPaneFactory.createScrollPane(myFrameworksTree), BorderLayout.CENTER);
    treePanel.setMinimumSize(JBUI.size(200, 300));

    splitter.setFirstComponent(treePanel);
    myOptionsPanel = new JPanel(new CardLayout());
    JPanel emptyCard = new JPanel();
    emptyCard.setPreferredSize(JBUI.size(400, 100));
    myOptionsPanel.add(EMPTY_CARD, emptyCard);

    splitter.setSecondComponent(myOptionsPanel);
    myFrameworksPanel.add(splitter, BorderLayout.CENTER);

    setProviders(providers);
  }

  public void setProviders(List<FrameworkSupportInModuleProvider> providers) {
    setProviders(providers, Collections.emptySet(), Collections.emptySet());
  }

  public void setProviders(List<FrameworkSupportInModuleProvider> providers, Set<String> associated, Set<String> preselected) {
    myProviders = providers;

    myAssociatedFrameworks = createNodes(myProviders, associated, preselected);
    for (FrameworkSupportNodeBase node : myRoots) {
      if (preselected.contains(node.getId())) {
        node.setChecked(true);
      }
    }
    setAssociatedFrameworks();

    myFrameworksTree.setRoots(myRoots);
    myFrameworksTree.setSelectionRow(0);
  }

  public void setAssociatedFrameworks() {

    if (myAssociatedFrameworksPanel == null) return;
    for (FrameworkSupportNodeBase nodeBase : myAssociatedFrameworks) {
      if (nodeBase instanceof FrameworkSupportNode) {
        ((FrameworkSupportNode)nodeBase).getConfigurable().onFrameworkSelectionChanged(true);
        FrameworkSupportOptionsComponent component = initializeOptionsPanel((FrameworkSupportNode)nodeBase, false);
        addAssociatedFrameworkComponent(component.getMainPanel(), myAssociatedFrameworksPanel);
      }
      else {
        JPanel panel = initializeGroupPanel((FrameworkGroup<?>)nodeBase.getUserObject(), false);
        addAssociatedFrameworkComponent(panel, myAssociatedFrameworksPanel);
      }
    }
  }

  private static void addAssociatedFrameworkComponent(JPanel component, JPanel panel) {
    panel.add(component, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0, GridBagConstraints.NORTHWEST,
                                                GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0));
  }

  protected void onFrameworkStateChanged() {}

  private void onSelectionChanged() {
    if (!myFrameworksTree.isProcessingMouseEventOnCheckbox()) {
      updateOptionsPanel();
    }
    
    final FrameworkSupportNodeBase selectedNode = getSelectedNode();
    if (!Comparing.equal(selectedNode, myLastSelectedNode)) {
      applyLibraryOptionsForSelected();

      myLastSelectedNode = selectedNode;
    }
  }

  @Override
  public void dispose() {
  }

  private void applyLibraryOptionsForSelected() {
    if (myLastSelectedNode instanceof FrameworkSupportNode) {
      final FrameworkSupportOptionsComponent optionsComponent = myInitializedOptionsComponents.get((FrameworkSupportNode)myLastSelectedNode);
      if (optionsComponent != null) {
        final LibraryOptionsPanel optionsPanel = optionsComponent.getLibraryOptionsPanel();
        if (optionsPanel != null) {
          optionsPanel.apply();
        }
      }
    }
  }

  private void updateOptionsPanel() {
    final FrameworkSupportNodeBase node = getSelectedNode();
    if (node instanceof FrameworkSupportNode) {
      FrameworkSupportNode frameworkSupportNode = (FrameworkSupportNode)node;
      initializeOptionsPanel(frameworkSupportNode, true);
      showCard(frameworkSupportNode.getId());
      UIUtil.setEnabled(myOptionsPanel, frameworkSupportNode.isChecked(), true);
      frameworkSupportNode.getConfigurable().onFrameworkSelectionChanged(node.isChecked());
    }
    else if (node instanceof FrameworkGroupNode) {
      FrameworkGroup<?> group = ((FrameworkGroupNode)node).getUserObject();
      initializeGroupPanel(group, true);
      showCard(group.getId());
      UIUtil.setEnabled(myOptionsPanel, true, true);
    }
    else {
      showCard(EMPTY_CARD);
    }
  }

  private JPanel initializeGroupPanel(FrameworkGroup<?> group, boolean addToOptions) {
    JPanel panel = myInitializedGroupPanels.get(group);
    if (panel == null) {
      FrameworkVersionComponent component = new FrameworkVersionComponent(myModel, group.getId(), group.getGroupVersions(), group.getPresentableName() + " version:");
      panel = component.getMainPanel();
      myInitializedGroupPanels.put(group, panel);
      if (addToOptions) {
        myOptionsPanel.add(group.getId(), wrapInScrollPane(panel));
      }
    }
    return panel;
  }

  @Nullable
  public FrameworkSupportNodeBase getSelectedNode() {
    final FrameworkSupportNodeBase[] nodes = myFrameworksTree.getSelectedNodes(FrameworkSupportNodeBase.class, null);
    return nodes.length == 1 ? nodes[0] : null;
  }

  private FrameworkSupportOptionsComponent initializeOptionsPanel(final FrameworkSupportNode node, boolean addToOptions) {
    FrameworkSupportOptionsComponent component = myInitializedOptionsComponents.get(node);
    if (component == null) {
      final FrameworkSupportNodeBase parentNode = node.getParentNode();
      if (parentNode instanceof FrameworkSupportNode) {
        initializeOptionsPanel((FrameworkSupportNode)parentNode, addToOptions);
      }
      else if (parentNode instanceof FrameworkGroupNode) {
        initializeGroupPanel(((FrameworkGroupNode)parentNode).getUserObject(), addToOptions);
      }

      component = new FrameworkSupportOptionsComponent(myModel, myLibrariesContainer, this,
                                                       node.getUserObject(), node.getConfigurable());
      if (addToOptions) {
        myOptionsPanel.add(node.getId(), wrapInScrollPane(component.getMainPanel()));
      }
      myInitializedOptionsComponents.put(node, component);
    }
    return component;
  }

  private static JScrollPane wrapInScrollPane(JPanel panel) {
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(panel);
    wrapper.setBorder(JBUI.Borders.empty(5));
    return ScrollPaneFactory.createScrollPane(wrapper, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                              ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
  }

  private void showCard(String cardName) {
    ((CardLayout)myOptionsPanel.getLayout()).show(myOptionsPanel, cardName);
  }

  private List<LibraryCompositionSettings> getLibrariesCompositionSettingsList() {
    List<LibraryCompositionSettings> list = new ArrayList<>();
    List<FrameworkSupportNode> selected = getSelectedNodes();
    for (FrameworkSupportNode node : selected) {
      ContainerUtil.addIfNotNull(list, getLibraryCompositionSettings(node));
    }
    return list;
  }

  @Nullable
  private LibraryCompositionSettings getLibraryCompositionSettings(FrameworkSupportNode node) {
    final FrameworkSupportOptionsComponent optionsComponent = myInitializedOptionsComponents.get(node);
    return optionsComponent != null ? optionsComponent.getLibraryCompositionSettings() : null;
  }

  private Collection<FrameworkSupportNodeBase> createNodes(List<FrameworkSupportInModuleProvider> providers,
                                                           Set<String> associated,
                                                           final Set<String> preselected) {
    Map<String, FrameworkSupportNode> nodes = new HashMap<>();
    Map<FrameworkGroup<?>, FrameworkGroupNode> groups = new HashMap<>();
    List<FrameworkSupportNodeBase> roots = new ArrayList<>();
    Map<String, FrameworkSupportNodeBase> associatedNodes = new LinkedHashMap<>();
    for (FrameworkSupportInModuleProvider provider : providers) {
      createNode(provider, nodes, groups, roots, providers, associated, associatedNodes);
    }

    FrameworkSupportNodeBase.sortByName(roots,
                                        (o1, o2) -> Comparing.compare(preselected.contains(o2.getId()), preselected.contains(o1.getId())));
    myRoots = roots;
    return associatedNodes.values();
  }

  @Nullable
  private FrameworkSupportNode createNode(final FrameworkSupportInModuleProvider provider,
                                          final Map<String, FrameworkSupportNode> nodes,
                                          final Map<FrameworkGroup<?>, FrameworkGroupNode> groupNodes,
                                          List<FrameworkSupportNodeBase> roots,
                                          List<FrameworkSupportInModuleProvider> providers,
                                          Set<String> associated,
                                          Map<String, FrameworkSupportNodeBase> associatedNodes) {
    String id = provider.getFrameworkType().getId();
    FrameworkSupportNode node = nodes.get(id);
    if (node != null || associatedNodes.containsKey(id)) {
      return node;
    }
    String underlyingTypeId = provider.getFrameworkType().getUnderlyingFrameworkTypeId();
    FrameworkSupportNodeBase parentNode = null;
    final FrameworkGroup<?> group = provider.getFrameworkType().getParentGroup();
    if (underlyingTypeId != null) {
      FrameworkSupportInModuleProvider parentProvider = FrameworkSupportUtil.findProvider(underlyingTypeId, providers);
      if (parentProvider == null) {
        LOG.info("Cannot find id = " + underlyingTypeId);
        return null;
      }
      parentNode = createNode(parentProvider, nodes, groupNodes, roots, providers, associated, associatedNodes);
    }
    else if (group != null) {
      parentNode = groupNodes.get(group);
      if (parentNode == null) {
        FrameworkGroupNode groupNode = new FrameworkGroupNode(group, null);
        if (associated.contains(groupNode.getId())) {
          associatedNodes.put(groupNode.getId(), groupNode);
        }
        else {
          groupNodes.put(group, groupNode);
          parentNode = groupNode;
          roots.add(groupNode);
        }
      }
    }
    node = new FrameworkSupportNode(provider, parentNode, myModel, this);
    if (associated.contains(id)) {
      associatedNodes.put(id, node);
    }
    else {
      nodes.put(id, node);
      if (parentNode == null) {
        roots.add(node);
      }
    }
    return node;
  }

  public JComponent getMainPanel() {
    return myMainPanel;
  }

  public FrameworksTree getFrameworksTree() {
    return myFrameworksTree;
  }

  public boolean hasSelectedFrameworks() {
    return !getSelectedNodes().isEmpty();
  }

  public List<FrameworkSupportNode> getSelectedNodes() {
    List<FrameworkSupportNode> list = new ArrayList<>();
    if (myRoots != null) {
      addChildFrameworks(myRoots, list);
    }
    list.addAll(ContainerUtil.mapNotNull(myAssociatedFrameworks, new Function.InstanceOf<>(FrameworkSupportNode.class)));
    return list;
  }

  private static void addChildFrameworks(final List<FrameworkSupportNodeBase> list, final List<FrameworkSupportNode> result) {
    for (FrameworkSupportNodeBase node : list) {
      if (node.isChecked() || node instanceof FrameworkGroupNode) {
        if (node instanceof FrameworkSupportNode) {
          result.add((FrameworkSupportNode)node);
        }
        //noinspection unchecked
        addChildFrameworks(node.getChildren(), result);
      }
    }
  }

  public boolean downloadLibraries(@NotNull final JComponent parentComponent) {
    applyLibraryOptionsForSelected();
    for (LibraryCompositionSettings compositionSettings : getLibrariesCompositionSettingsList()) {
      if (!compositionSettings.downloadFiles(parentComponent)) {
        int answer = Messages.showYesNoDialog(parentComponent,
                                              ProjectBundle.message("warning.message.some.required.libraries.wasn.t.downloaded"),
                                              CommonBundle.getWarningTitle(), Messages.getWarningIcon());
        return answer == Messages.YES;
      }
    }

    return true;
  }

  public boolean validate() {
    applyLibraryOptionsForSelected();
    List<String> frameworksWithoutRequiredLibraries = new ArrayList<>();
    for (FrameworkSupportNode node : getSelectedNodes()) {
      if (node.getConfigurable().isOnlyLibraryAdded()) {
        LibraryCompositionSettings librarySettings = getLibraryCompositionSettings(node);
        if (librarySettings != null && !librarySettings.isLibraryConfigured()) {
          frameworksWithoutRequiredLibraries.add(node.getTitle());
        }
      }
    }

    if (!frameworksWithoutRequiredLibraries.isEmpty()) {
      String frameworksText = StringUtil.join(frameworksWithoutRequiredLibraries, ", ");
      Messages.showErrorDialog(myMainPanel, ProjectBundle.message("error.message.required.library.is.not.configured", frameworksText, frameworksWithoutRequiredLibraries.size()),
                               ProjectBundle.message("error.title.required.library.is.not.configured"));
      return false;
    }
    return true;
  }

  public void addSupport(final @NotNull Module module, final @NotNull ModifiableRootModel rootModel) {
    List<Library> addedLibraries = new ArrayList<>();
    List<FrameworkSupportNode> selectedFrameworks = getSelectedNodes();
    sortFrameworks(selectedFrameworks);
    List<FrameworkSupportConfigurable> selectedConfigurables = new ArrayList<>();
    final IdeaModifiableModelsProvider modifiableModelsProvider = new IdeaModifiableModelsProvider();
    for (FrameworkSupportNode node : selectedFrameworks) {
      FrameworkSupportInModuleConfigurable configurable = node.getConfigurable();
      if (configurable instanceof OldFrameworkSupportProviderWrapper.FrameworkSupportConfigurableWrapper) {
        selectedConfigurables.add(((OldFrameworkSupportProviderWrapper.FrameworkSupportConfigurableWrapper)configurable).getConfigurable());
      }
      final LibraryCompositionSettings settings = getLibraryCompositionSettings(node);
      Library library = settings != null ? settings.addLibraries(rootModel, addedLibraries, myLibrariesContainer) : null;
      if (configurable instanceof OldFrameworkSupportProviderWrapper.FrameworkSupportConfigurableWrapper) {
        ((OldFrameworkSupportProviderWrapper.FrameworkSupportConfigurableWrapper)configurable).getConfigurable().addSupport(module, rootModel,
                                                                                                                         library);
      }
      else {
        configurable.addSupport(module, rootModel, modifiableModelsProvider);
      }
    }
    for (FrameworkSupportNode node : selectedFrameworks) {
      FrameworkSupportInModuleProvider provider = node.getUserObject();
      if (provider instanceof OldFrameworkSupportProviderWrapper) {
        final FrameworkSupportProvider oldProvider = ((OldFrameworkSupportProviderWrapper)provider).getProvider();
        if (oldProvider instanceof FacetBasedFrameworkSupportProvider && !addedLibraries.isEmpty()) {
          ((FacetBasedFrameworkSupportProvider)oldProvider).processAddedLibraries(module, addedLibraries);
        }
      }
    }
    for (FrameworkSupportCommunicator communicator : FrameworkSupportCommunicator.EP_NAME.getExtensions()) {
      communicator.onFrameworkSupportAdded(module, rootModel, selectedConfigurables, myModel);
    }
  }

  private void sortFrameworks(final List<FrameworkSupportNode> nodes) {
    final Comparator<FrameworkSupportInModuleProvider> comparator = FrameworkSupportUtil.getFrameworkSupportProvidersComparator(myProviders);
    Collections.sort(nodes, (o1, o2) -> comparator.compare(o1.getUserObject(), o2.getUserObject()));
  }
}
