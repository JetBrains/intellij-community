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

package com.intellij.ide.util.newProjectWizard;

import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings;
import com.intellij.facet.impl.ui.libraries.LibraryOptionsPanel;
import com.intellij.facet.ui.FacetBasedFrameworkSupportProvider;
import com.intellij.framework.FrameworkGroup;
import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportUtil;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportCommunicator;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.IdeaModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
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
  private List<FrameworkSupportNodeBase> myRoots;
  private final LibrariesContainer myLibrariesContainer;
  private final List<FrameworkSupportInModuleProvider> myProviders;
  private final FrameworkSupportModelBase myModel;
  private final JPanel myOptionsPanel;
  private final FrameworksTree myFrameworksTree;
  private final Map<FrameworkSupportNode, FrameworkSupportOptionsComponent> myInitializedOptionsComponents = new HashMap<FrameworkSupportNode, FrameworkSupportOptionsComponent>();
  private final Map<FrameworkGroup<?>, JPanel> myInitializedGroupPanels = new HashMap<FrameworkGroup<?>, JPanel>();
  private FrameworkSupportNodeBase myLastSelectedNode;

  public AddSupportForFrameworksPanel(final List<FrameworkSupportInModuleProvider> providers, final FrameworkSupportModelBase model) {
    myModel = model;
    myLibrariesContainer = model.getLibrariesContainer();
    myProviders = providers;
    createNodes();

    final Splitter splitter = new Splitter(false, 0.30f, 0.1f, 0.7f);
    myFrameworksTree = new FrameworksTree(myRoots) {
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
    myFrameworksTree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        onSelectionChanged();
      }
    });

    splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myFrameworksTree));
    myOptionsPanel = new JPanel(new CardLayout());
    myOptionsPanel.add(EMPTY_CARD, new JPanel());

    splitter.setSecondComponent(myOptionsPanel);
    myFrameworksPanel.add(splitter, BorderLayout.CENTER);

    myFrameworksTree.setSelectionRow(0);    
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
      initializeOptionsPanel(frameworkSupportNode);
      showCard(frameworkSupportNode.getProvider().getFrameworkType().getId());
      UIUtil.setEnabled(myOptionsPanel, frameworkSupportNode.isChecked(), true);
      frameworkSupportNode.getConfigurable().onFrameworkSelectionChanged(node.isChecked());
    }
    else if (node instanceof FrameworkGroupNode) {
      FrameworkGroup<?> group = ((FrameworkGroupNode)node).getGroup();
      initializeGroupPanel(group);
      showCard(group.getId());
      UIUtil.setEnabled(myOptionsPanel, true, true);
    }
    else {
      showCard(EMPTY_CARD);
    }
  }

  private void initializeGroupPanel(FrameworkGroup<?> group) {
    if (!myInitializedGroupPanels.containsKey(group)) {
      FrameworkVersionComponent component = new FrameworkVersionComponent(myModel, group.getId(), group.getGroupVersions());
      myInitializedGroupPanels.put(group, component.getMainPanel());
      myOptionsPanel.add(group.getId(), component.getMainPanel());
    }
  }

  @Nullable
  private FrameworkSupportNodeBase getSelectedNode() {
    final FrameworkSupportNodeBase[] nodes = myFrameworksTree.getSelectedNodes(FrameworkSupportNodeBase.class, null);
    return nodes.length == 1 ? nodes[0] : null;
  }

  private void initializeOptionsPanel(final FrameworkSupportNode node) {
    if (!myInitializedOptionsComponents.containsKey(node)) {
      final FrameworkSupportNodeBase parentNode = node.getParentNode();
      if (parentNode instanceof FrameworkSupportNode) {
        initializeOptionsPanel((FrameworkSupportNode)parentNode);
      }
      else if (parentNode instanceof FrameworkGroupNode) {
        initializeGroupPanel(((FrameworkGroupNode)parentNode).getGroup());
      }

      FrameworkSupportOptionsComponent optionsComponent = new FrameworkSupportOptionsComponent(myModel, myLibrariesContainer, this,
                                                                                               node.getProvider(), node.getConfigurable());
      final String id = node.getProvider().getFrameworkType().getId();
      myOptionsPanel.add(id, optionsComponent.getMainPanel());
      myInitializedOptionsComponents.put(node, optionsComponent);
    }
  }

  private void showCard(String cardName) {
    ((CardLayout)myOptionsPanel.getLayout()).show(myOptionsPanel, cardName);
  }

  private List<LibraryCompositionSettings> getLibrariesCompositionSettingsList() {
    List<LibraryCompositionSettings> list = new ArrayList<LibraryCompositionSettings>();
    List<FrameworkSupportNode> selected = getFrameworkNodes(true);
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

  public boolean downloadLibraries() {
    applyLibraryOptionsForSelected();
    List<LibraryCompositionSettings> list = getLibrariesCompositionSettingsList();
    for (LibraryCompositionSettings compositionSettings : list) {
      if (!compositionSettings.downloadFiles(myMainPanel)) return false;
    }
    return true;
  }

  private void createNodes() {
    Map<String, FrameworkSupportNode> nodes = new HashMap<String, FrameworkSupportNode>();
    Map<FrameworkGroup<?>, FrameworkGroupNode> groups = new HashMap<FrameworkGroup<?>, FrameworkGroupNode>();
    List<FrameworkSupportNodeBase> roots = new ArrayList<FrameworkSupportNodeBase>();
    for (FrameworkSupportInModuleProvider provider : myProviders) {
      createNode(provider, nodes, groups, roots);
    }

    FrameworkSupportNodeBase.sortByName(roots);
    myRoots = roots;
  }

  @Nullable
  private FrameworkSupportNode createNode(final FrameworkSupportInModuleProvider provider, final Map<String, FrameworkSupportNode> nodes,
                                          final Map<FrameworkGroup<?>, FrameworkGroupNode> groupNodes,
                                          List<FrameworkSupportNodeBase> roots) {
    FrameworkSupportNode node = nodes.get(provider.getFrameworkType().getId());
    if (node == null) {
      String underlyingTypeId = provider.getFrameworkType().getUnderlyingFrameworkTypeId();
      FrameworkSupportNodeBase parentNode = null;
      final FrameworkGroup<?> group = provider.getFrameworkType().getParentGroup();
      if (underlyingTypeId != null) {
        FrameworkSupportInModuleProvider parentProvider = FrameworkSupportUtil.findProvider(underlyingTypeId, myProviders);
        if (parentProvider == null) {
          LOG.info("Cannot find id = " + underlyingTypeId);
          return null;
        }
        parentNode = createNode(parentProvider, nodes, groupNodes, roots);
      }
      else if (group != null) {
        parentNode = groupNodes.get(group);
        if (parentNode == null) {
          FrameworkGroupNode groupNode = new FrameworkGroupNode(group, null);
          groupNodes.put(group, groupNode);
          parentNode = groupNode;
          roots.add(groupNode);
        }
      }
      node = new FrameworkSupportNode(provider, parentNode, myModel, this);
      nodes.put(provider.getFrameworkType().getId(), node);
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
    return !getFrameworkNodes(true).isEmpty();
  }

  private List<FrameworkSupportNode> getFrameworkNodes(final boolean selectedOnly) {
    List<FrameworkSupportNode> list = new ArrayList<FrameworkSupportNode>();
    if (myRoots != null) {
      addChildFrameworks(myRoots, list, selectedOnly);
    }
    return list;
  }

  private static void addChildFrameworks(final List<FrameworkSupportNodeBase> list, final List<FrameworkSupportNode> result,
                                         final boolean selectedOnly) {
    for (FrameworkSupportNodeBase node : list) {
      if (!selectedOnly || node.isChecked()) {
        if (node instanceof FrameworkSupportNode) {
          result.add((FrameworkSupportNode)node);
        }
        addChildFrameworks(node.getChildren(), result, selectedOnly);
      }
    }
  }

  public void addSupport(final @NotNull Module module, final @NotNull ModifiableRootModel rootModel) {
    List<Library> addedLibraries = new ArrayList<Library>();
    List<FrameworkSupportNode> selectedFrameworks = getFrameworkNodes(true);
    sortFrameworks(selectedFrameworks);
    List<FrameworkSupportConfigurable> selectedConfigurables = new ArrayList<FrameworkSupportConfigurable>();
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
      FrameworkSupportInModuleProvider provider = node.getProvider();
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
    Collections.sort(nodes, new Comparator<FrameworkSupportNode>() {
      public int compare(final FrameworkSupportNode o1, final FrameworkSupportNode o2) {
        return comparator.compare(o1.getProvider(), o2.getProvider());
      }
    });
  }
}
