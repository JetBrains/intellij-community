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
import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportUtil;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportCommunicator;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.MultiValuesMap;
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
  private List<List<FrameworkSupportNode>> myGroups;
  private final LibrariesContainer myLibrariesContainer;
  private final List<FrameworkSupportProvider> myProviders;
  private final FrameworkSupportModelBase myModel;
  private final JPanel myOptionsPanel;
  private final FrameworksTree myFrameworksTree;
  private final Map<FrameworkSupportNode, FrameworkSupportOptionsComponent> myInitializedOptionsComponents = new HashMap<FrameworkSupportNode, FrameworkSupportOptionsComponent>();
  private FrameworkSupportNode myLastSelectedNode;

  public AddSupportForFrameworksPanel(final List<FrameworkSupportProvider> providers, final FrameworkSupportModelBase model) {
    myModel = model;
    myLibrariesContainer = model.getLibrariesContainer();
    myProviders = providers;
    createNodes();

    final Splitter splitter = new Splitter(false, 0.30f, 0.1f, 0.7f);
    myFrameworksTree = new FrameworksTree(myGroups) {
      @Override
      protected void onNodeStateChanged(CheckedTreeNode node) {
        if (!(node instanceof FrameworkSupportNode)) return;

        final FrameworkSupportNode frameworkSupportNode = (FrameworkSupportNode)node;
        frameworkSupportNode.setConfigurableComponentEnabled(node.isChecked());
        updateOptionsPanel();
        frameworkSupportNode.getConfigurable().onFrameworkSelectionChanged(node.isChecked());
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
    
    final FrameworkSupportNode selectedNode = getSelectedNode();
    if (!Comparing.equal(selectedNode, myLastSelectedNode)) {
      applyLibraryOptionsForSelected();

      myLastSelectedNode = selectedNode;
    }
  }

  @Override
  public void dispose() {
  }

  private void applyLibraryOptionsForSelected() {
    if (myLastSelectedNode != null) {
      final FrameworkSupportOptionsComponent optionsComponent = myInitializedOptionsComponents.get(myLastSelectedNode);
      if (optionsComponent != null) {
        final LibraryOptionsPanel optionsPanel = optionsComponent.getLibraryOptionsPanel();
        if (optionsPanel != null) {
          optionsPanel.apply();
        }
      }
    }
  }

  private void updateOptionsPanel() {
    final FrameworkSupportNode node = getSelectedNode();
    if (node != null) {
      initializeOptionsPanel(node);
      showCard(node.getProvider().getId());
      UIUtil.setEnabled(myOptionsPanel, node.isChecked(), true);
    }
    else {
      showCard(EMPTY_CARD);
    }
  }

  @Nullable
  private FrameworkSupportNode getSelectedNode() {
    final FrameworkSupportNode[] nodes = myFrameworksTree.getSelectedNodes(FrameworkSupportNode.class, null);
    return nodes.length == 1 ? nodes[0] : null;
  }

  private void initializeOptionsPanel(final FrameworkSupportNode node) {
    if (!myInitializedOptionsComponents.containsKey(node)) {
      FrameworkSupportOptionsComponent optionsComponent = new FrameworkSupportOptionsComponent(myModel, myLibrariesContainer, node, this);
      final String id = node.getProvider().getId();
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
    MultiValuesMap<String, FrameworkSupportNode> groups = new MultiValuesMap<String, FrameworkSupportNode>(true);
    for (FrameworkSupportProvider frameworkSupport : myProviders) {
      createNode(frameworkSupport, nodes, groups);
    }

    myGroups = new ArrayList<List<FrameworkSupportNode>>();
    for (String groupId : groups.keySet()) {
      final Collection<FrameworkSupportNode> collection = groups.get(groupId);
      if (collection != null) {
        final List<FrameworkSupportNode> group = new ArrayList<FrameworkSupportNode>();
        for (FrameworkSupportNode node : collection) {
          if (node.getParentNode() == null) {
            group.add(node);
          }
        }
        FrameworkSupportNode.sortByTitle(group);
        myGroups.add(group);
      }
    }
  }

  @Nullable
  private FrameworkSupportNode createNode(final FrameworkSupportProvider provider, final Map<String, FrameworkSupportNode> nodes,
                                              final MultiValuesMap<String, FrameworkSupportNode> groups) {
    FrameworkSupportNode node = nodes.get(provider.getId());
    if (node == null) {
      String underlyingFrameworkId = provider.getUnderlyingFrameworkId();
      FrameworkSupportNode parentNode = null;
      if (underlyingFrameworkId != null) {
        FrameworkSupportProvider parentProvider = FrameworkSupportUtil.findProvider(underlyingFrameworkId, myProviders);
        if (parentProvider == null) {
          LOG.info("Cannot find id = " + underlyingFrameworkId);
          return null;
        }
        parentNode = createNode(parentProvider, nodes, groups);
      }
      node = new FrameworkSupportNode(provider, parentNode, myModel, this);
      nodes.put(provider.getId(), node);
      groups.put(provider.getGroupId(), node);
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
    ArrayList<FrameworkSupportNode> list = new ArrayList<FrameworkSupportNode>();
    if (myGroups != null) {
      for (List<FrameworkSupportNode> group : myGroups) {
        addChildFrameworks(group, list, selectedOnly);
      }
    }
    return list;
  }

  private static void addChildFrameworks(final List<FrameworkSupportNode> list, final List<FrameworkSupportNode> result,
                                         final boolean selectedOnly) {
    for (FrameworkSupportNode node : list) {
      if (!selectedOnly || node.isChecked()) {
        result.add(node);
        addChildFrameworks(node.getChildren(), result, selectedOnly);
      }
    }
  }

  public void addSupport(final @NotNull Module module, final @NotNull ModifiableRootModel rootModel) {
    List<Library> addedLibraries = new ArrayList<Library>();
    List<FrameworkSupportNode> selectedFrameworks = getFrameworkNodes(true);
    sortFrameworks(selectedFrameworks);
    List<FrameworkSupportConfigurable> selectedConfigurables = new ArrayList<FrameworkSupportConfigurable>();
    for (FrameworkSupportNode node : selectedFrameworks) {
      FrameworkSupportConfigurable configurable = node.getConfigurable();
      selectedConfigurables.add(configurable);
      final LibraryCompositionSettings settings = getLibraryCompositionSettings(node);
      Library library = settings != null ? settings.addLibraries(rootModel, addedLibraries, myLibrariesContainer) : null;
      configurable.addSupport(module, rootModel, library);
    }
    for (FrameworkSupportNode node : selectedFrameworks) {
      FrameworkSupportProvider provider = node.getProvider();
      if (provider instanceof FacetBasedFrameworkSupportProvider && !addedLibraries.isEmpty()) {
        ((FacetBasedFrameworkSupportProvider)provider).processAddedLibraries(module, addedLibraries);
      }
    }
    for (FrameworkSupportCommunicator communicator : FrameworkSupportCommunicator.EP_NAME.getExtensions()) {
      communicator.onFrameworkSupportAdded(module, rootModel, selectedConfigurables, myModel);
    }
  }

  private void sortFrameworks(final List<FrameworkSupportNode> nodes) {
    final Comparator<FrameworkSupportProvider> comparator = FrameworkSupportUtil.getFrameworkSupportProvidersComparator(myProviders);
    Collections.sort(nodes, new Comparator<FrameworkSupportNode>() {
      public int compare(final FrameworkSupportNode o1, final FrameworkSupportNode o2) {
        return comparator.compare(o1.getProvider(), o2.getProvider());
      }
    });
  }
}
