/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.impl.TypeSafeDataProviderAdapter;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.artifacts.actions.*;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.LibrarySourceItem;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.ModuleOutputSourceItem;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.SourceItemsTree;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.elements.ArchivePackagingElement;
import com.intellij.packaging.impl.elements.ManifestFileUtil;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.util.EventDispatcher;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ThreeStateCheckBox;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactEditorImpl implements ArtifactEditorEx {
  private JPanel myMainPanel;
  private JCheckBox myBuildOnMakeCheckBox;
  private TextFieldWithBrowseButton myOutputDirectoryField;    
  private JPanel myEditorPanel;
  private JPanel myErrorPanelPlace;
  private ThreeStateCheckBox myShowContentCheckBox;
  private FixedSizeButton myShowSpecificContentOptionsButton;
  private JPanel myTopPanel;
  private final ActionGroup myShowSpecificContentOptionsGroup;
  private final Project myProject;
  private final ComplexElementSubstitutionParameters mySubstitutionParameters = new ComplexElementSubstitutionParameters();
  private final EventDispatcher<ArtifactEditorListener> myDispatcher = EventDispatcher.create(ArtifactEditorListener.class);
  private final ArtifactEditorContextImpl myContext;
  private final SourceItemsTree mySourceItemsTree;
  private final Artifact myOriginalArtifact;
  private final LayoutTreeComponent myLayoutTreeComponent;
  private TabbedPaneWrapper myTabbedPane;
  private ArtifactPropertiesEditors myPropertiesEditors;
  private final ArtifactValidationManagerImpl myValidationManager;
  private boolean myDisposed;

  public ArtifactEditorImpl(final @NotNull ArtifactsStructureConfigurableContext context, @NotNull Artifact artifact, @NotNull ArtifactEditorSettings settings) {
    myContext = createArtifactEditorContext(context);
    myOriginalArtifact = artifact;
    myProject = context.getProject();
    mySubstitutionParameters.setTypesToShowContent(settings.getTypesToShowContent());
    mySourceItemsTree = new SourceItemsTree(myContext, this);
    myLayoutTreeComponent = new LayoutTreeComponent(this, mySubstitutionParameters, myContext, myOriginalArtifact, settings.isSortElements());
    myPropertiesEditors = new ArtifactPropertiesEditors(myContext, myOriginalArtifact, myOriginalArtifact);
    Disposer.register(this, mySourceItemsTree);
    Disposer.register(this, myLayoutTreeComponent);
    myTopPanel.setBorder(new EmptyBorder(0, 10, 0, 10));
    myBuildOnMakeCheckBox.setSelected(artifact.isBuildOnMake());
    final String outputPath = artifact.getOutputPath();
    myOutputDirectoryField.addBrowseFolderListener(CompilerBundle.message("dialog.title.output.directory.for.artifact"),
                                                   CompilerBundle.message("chooser.description.select.output.directory.for.0.artifact",
                                                                          getArtifact().getName()), myProject,
                                                   FileChooserDescriptorFactory.createSingleFolderDescriptor());
    myShowSpecificContentOptionsGroup = createShowSpecificContentOptionsGroup();
    myShowSpecificContentOptionsButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, myShowSpecificContentOptionsGroup).getComponent().show(myShowSpecificContentOptionsButton, 0, 0);
      }
    });
    setOutputPath(outputPath);
    myValidationManager = new ArtifactValidationManagerImpl(this);
    updateShowContentCheckbox();
  }

  protected ArtifactEditorContextImpl createArtifactEditorContext(ArtifactsStructureConfigurableContext parentContext) {
    return new ArtifactEditorContextImpl(parentContext, this);
  }

  private ActionGroup createShowSpecificContentOptionsGroup() {
    final DefaultActionGroup group = new DefaultActionGroup();
    for (ComplexPackagingElementType<?> type : PackagingElementFactory.getInstance().getComplexElementTypes()) {
      group.add(new ToggleShowElementContentAction(type, this));
    }
    return group;
  }

  private void setOutputPath(@Nullable String outputPath) {
    myOutputDirectoryField.setText(outputPath != null ? FileUtil.toSystemDependentName(outputPath) : null);
  }

  public void apply() {
    final ModifiableArtifact modifiableArtifact = myContext.getOrCreateModifiableArtifactModel().getOrCreateModifiableArtifact(myOriginalArtifact);
    modifiableArtifact.setBuildOnMake(myBuildOnMakeCheckBox.isSelected());
    modifiableArtifact.setOutputPath(getConfiguredOutputPath());
    myPropertiesEditors.applyProperties();
    myLayoutTreeComponent.saveElementProperties();
  }

  @Nullable
  private String getConfiguredOutputPath() {
    String outputPath = FileUtil.toSystemIndependentName(myOutputDirectoryField.getText().trim());
    if (outputPath.length() == 0) {
      outputPath = null;
    }
    return outputPath;
  }

  public SourceItemsTree getSourceItemsTree() {
    return mySourceItemsTree;
  }

  public void addListener(@NotNull final ArtifactEditorListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public ArtifactEditorContextImpl getContext() {
    return myContext;
  }

  public void removeListener(@NotNull final ArtifactEditorListener listener) {
    myDispatcher.removeListener(listener);
  }

  @Override
  public Artifact getArtifact() {
    return myContext.getArtifactModel().getArtifactByOriginal(myOriginalArtifact);
  }

  @Override
  public CompositePackagingElement<?> getRootElement() {
    return myLayoutTreeComponent.getRootElement();
  }

  @Override
  public void rebuildTries() {
    myLayoutTreeComponent.rebuildTree();
    mySourceItemsTree.rebuildTree();
  }

  @Override
  public void queueValidation() {
    myContext.queueValidation();
  }

  public JComponent createMainComponent() {
    mySourceItemsTree.initTree();
    myLayoutTreeComponent.initTree();
    DataManager.registerDataProvider(myMainPanel, new TypeSafeDataProviderAdapter(new MyDataProvider()));

    myErrorPanelPlace.add(myValidationManager.getMainErrorPanel(), BorderLayout.CENTER);

    final JBSplitter splitter = new OnePixelSplitter(false);
    final JPanel leftPanel = new JPanel(new BorderLayout());
    JPanel treePanel = myLayoutTreeComponent.getTreePanel();
    if (UIUtil.isUnderDarcula()) {
      treePanel.setBorder(new EmptyBorder(3, 0, 0, 0));
    } else {
      treePanel.setBorder(new LineBorder(UIUtil.getBorderColor()));
    }
    leftPanel.add(treePanel, BorderLayout.CENTER);
    if (UIUtil.isUnderDarcula()) {
      CompoundBorder border =
        new CompoundBorder(new CustomLineBorder(0, 0, 0, 1), BorderFactory.createEmptyBorder(0, 0, 0, 0));
      leftPanel.setBorder(border);
    } else {
      leftPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 0));
    }
    splitter.setFirstComponent(leftPanel);

    final JPanel rightPanel = new JPanel(new BorderLayout());
    final JPanel rightTopPanel = new JPanel(new BorderLayout());
    final JPanel labelPanel = new JPanel();
    labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.X_AXIS));
    labelPanel.add(new JLabel("Available Elements "));
    final HyperlinkLabel link = new HyperlinkLabel("");
    link.setIcon(AllIcons.General.Help_small);
    link.setUseIconAsLink(true);
    link.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        final JLabel label = new JLabel(ProjectBundle.message("artifact.source.items.tree.tooltip"));
        label.setBorder(HintUtil.createHintBorder());
        label.setBackground(HintUtil.INFORMATION_COLOR);
        label.setOpaque(true);
        HintManager.getInstance().showHint(label, RelativePoint.getSouthWestOf(link), HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE, -1);
      }
    });
    labelPanel.add(link);
    rightTopPanel.add(labelPanel, BorderLayout.CENTER);
    rightPanel.add(rightTopPanel, BorderLayout.NORTH);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(mySourceItemsTree, true);
    JPanel scrollPaneWrap = new JPanel(new BorderLayout());
    scrollPaneWrap.add(scrollPane, BorderLayout.CENTER);
    if (UIUtil.isUnderDarcula()) {
      scrollPaneWrap.setBorder(new EmptyBorder(3, 0, 0, 0));
    } else {
      scrollPaneWrap.setBorder(new LineBorder(UIUtil.getBorderColor()));
    }

    rightPanel.add(scrollPaneWrap, BorderLayout.CENTER);
    if (UIUtil.isUnderDarcula()) {
      rightPanel.setBorder(new CompoundBorder(new CustomLineBorder(0, 1, 0, 0), BorderFactory.createEmptyBorder(0, 0, 0, 0)));
    } else {
      rightPanel.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 3));
    }
    splitter.setSecondComponent(rightPanel);
    splitter.getDivider().setBackground(UIUtil.getPanelBackground());
    treePanel.setBorder(JBUI.Borders.empty());
    rightPanel.setBorder(JBUI.Borders.empty());
    scrollPaneWrap.setBorder(JBUI.Borders.empty());
    leftPanel.setBorder(JBUI.Borders.empty());


    myShowContentCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final ThreeStateCheckBox.State state = myShowContentCheckBox.getState();
        if (state == ThreeStateCheckBox.State.SELECTED) {
          mySubstitutionParameters.setSubstituteAll();
        }
        else if (state == ThreeStateCheckBox.State.NOT_SELECTED) {
          mySubstitutionParameters.setSubstituteNone();
        }
        myShowContentCheckBox.setThirdStateEnabled(false);
        myLayoutTreeComponent.rebuildTree();
        onShowContentSettingsChanged();
      }
    });

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, createToolbarActionGroup(), true);
    JComponent toolbarComponent = toolbar.getComponent();
    if (UIUtil.isUnderDarcula()) {
      toolbarComponent.setBorder(new CustomLineBorder(0,0,1,0));
    }
    leftPanel.add(toolbarComponent, BorderLayout.NORTH);
    toolbar.updateActionsImmediately();
    rightTopPanel.setPreferredSize(new Dimension(-1, toolbarComponent.getPreferredSize().height));

    myTabbedPane = new TabbedPaneWrapper(this);
    myTabbedPane.addTab("Output Layout", splitter);
    myPropertiesEditors.addTabs(myTabbedPane);
    myEditorPanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);

    final LayoutTree tree = myLayoutTreeComponent.getLayoutTree();
    new ShowAddPackagingElementPopupAction(this).registerCustomShortcutSet(CommonShortcuts.getNew(), tree);
    PopupHandler.installPopupHandler(tree, createPopupActionGroup(), ActionPlaces.UNKNOWN, ActionManager.getInstance());
    ToolTipManager.sharedInstance().registerComponent(tree);
    rebuildTries();
    return getMainComponent();
  }

  private void onShowContentSettingsChanged() {
    myContext.getParent().getDefaultSettings().setTypesToShowContent(mySubstitutionParameters.getTypesToSubstitute());
  }

  public void updateShowContentCheckbox() {
    final ThreeStateCheckBox.State state;
    if (mySubstitutionParameters.isAllSubstituted()) {
      state = ThreeStateCheckBox.State.SELECTED;
    }
    else if (mySubstitutionParameters.isNoneSubstituted()) {
      state = ThreeStateCheckBox.State.NOT_SELECTED;
    }
    else {
      state = ThreeStateCheckBox.State.DONT_CARE;
    }
    myShowContentCheckBox.setThirdStateEnabled(state == ThreeStateCheckBox.State.DONT_CARE);
    myShowContentCheckBox.setState(state);
    onShowContentSettingsChanged();
  }

  public ArtifactEditorSettings createSettings() {
    return new ArtifactEditorSettings(myLayoutTreeComponent.isSortElements(), mySubstitutionParameters.getTypesToSubstitute());
  }

  private DefaultActionGroup createToolbarActionGroup() {
    final DefaultActionGroup toolbarActionGroup = new DefaultActionGroup();

    final List<AnAction> createActions = new ArrayList<>(createNewElementActions());
    for (AnAction createAction : createActions) {
      toolbarActionGroup.add(createAction);
    }

    toolbarActionGroup.add(new RemovePackagingElementAction(this));
    toolbarActionGroup.add(Separator.getInstance());
    toolbarActionGroup.add(new SortElementsToggleAction(this.getLayoutTreeComponent()));
    toolbarActionGroup.add(new MovePackagingElementAction(myLayoutTreeComponent, "Move Up", "", IconUtil.getMoveUpIcon(), -1));
    toolbarActionGroup.add(new MovePackagingElementAction(myLayoutTreeComponent, "Move Down", "", IconUtil.getMoveDownIcon(), 1));
    return toolbarActionGroup;
  }

  public List<AnAction> createNewElementActions() {
    final List<AnAction> createActions = new ArrayList<>();
    AddCompositeElementAction.addCompositeCreateActions(createActions, this);
    createActions.add(createAddNonCompositeElementGroup());
    return createActions;
  }

  private DefaultActionGroup createPopupActionGroup() {
    final LayoutTree tree = myLayoutTreeComponent.getLayoutTree();

    DefaultActionGroup popupActionGroup = new DefaultActionGroup();
    final List<AnAction> createActions = new ArrayList<>();
    AddCompositeElementAction.addCompositeCreateActions(createActions, this);
    for (AnAction createAction : createActions) {
      popupActionGroup.add(createAction);
    }
    popupActionGroup.add(createAddNonCompositeElementGroup());
    final RemovePackagingElementAction removeAction = new RemovePackagingElementAction(this);
    removeAction.registerCustomShortcutSet(CommonShortcuts.getDelete(), tree);
    popupActionGroup.add(removeAction);
    popupActionGroup.add(new ExtractArtifactAction(this));
    popupActionGroup.add(new InlineArtifactAction(this));
    popupActionGroup.add(new RenamePackagingElementAction(this));
    popupActionGroup.add(new SurroundElementWithAction(this));
    popupActionGroup.add(Separator.getInstance());
    popupActionGroup.add(new HideContentAction(this));
    popupActionGroup.add(new LayoutTreeNavigateAction(myLayoutTreeComponent));
    popupActionGroup.add(new LayoutTreeFindUsagesAction(myLayoutTreeComponent, myProject, myContext.getParent()));

    popupActionGroup.add(Separator.getInstance());
    CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    DefaultTreeExpander treeExpander = new DefaultTreeExpander(tree);
    popupActionGroup.add(actionsManager.createExpandAllAction(treeExpander, tree));
    popupActionGroup.add(actionsManager.createCollapseAllAction(treeExpander, tree));
    return popupActionGroup;
  }

  @Override
  public ComplexElementSubstitutionParameters getSubstitutionParameters() {
    return mySubstitutionParameters;
  }

  private ActionGroup createAddNonCompositeElementGroup() {
    DefaultActionGroup group = new DefaultActionGroup(ProjectBundle.message("artifacts.add.copy.action"), true);
    group.getTemplatePresentation().setIcon(IconUtil.getAddIcon());
    for (PackagingElementType<?> type : PackagingElementFactory.getInstance().getNonCompositeElementTypes()) {
      group.add(new AddNewPackagingElementAction(type, this));
    }
    return group;
  }

  @Override
  public JComponent getMainComponent() {
    return myMainPanel;
  }

  @Override
  public void addNewPackagingElement(@NotNull PackagingElementType<?> type) {
    myLayoutTreeComponent.addNewPackagingElement(type);
    mySourceItemsTree.rebuildTree();
  }

  @Override
  public void removeSelectedElements() {
    myLayoutTreeComponent.removeSelectedElements();
  }

  @Override
  public void removePackagingElement(@NotNull final String pathToParent, @NotNull final PackagingElement<?> element) {
    doReplaceElement(pathToParent, element, null);
  }

  @Override
  public void replacePackagingElement(@NotNull final String pathToParent,
                                      @NotNull final PackagingElement<?> element,
                                      @NotNull final PackagingElement<?> replacement) {
    doReplaceElement(pathToParent, element, replacement);
  }

  private void doReplaceElement(final @NotNull String pathToParent, final @NotNull PackagingElement<?> element, final @Nullable PackagingElement replacement) {
    myLayoutTreeComponent.editLayout(() -> {
      final CompositePackagingElement<?> parent = findCompositeElementByPath(pathToParent);
      if (parent == null) return;
      for (PackagingElement<?> child : parent.getChildren()) {
        if (child.isEqualTo(element)) {
          parent.removeChild(child);
          if (replacement != null) {
            parent.addOrFindChild(replacement);
          }
          break;
        }
      }
    });
    myLayoutTreeComponent.rebuildTree();
  }

  @Nullable
  private CompositePackagingElement<?> findCompositeElementByPath(String pathToElement) {
    CompositePackagingElement<?> element = getRootElement();
    for (String name : StringUtil.split(pathToElement, "/")) {
      element = element.findCompositeChild(name);
      if (element == null) return null;
    }
    return element;
  }

  public boolean isModified() {
    return myBuildOnMakeCheckBox.isSelected() != myOriginalArtifact.isBuildOnMake()
        || !Comparing.equal(getConfiguredOutputPath(), myOriginalArtifact.getOutputPath())
        || myPropertiesEditors.isModified()
        || myLayoutTreeComponent.isPropertiesModified();
  }

  @Override
  public void dispose() {
    myDisposed = true;
  }

  @Override
  public boolean isDisposed() {
    return myDisposed;
  }

  @Override
  public LayoutTreeComponent getLayoutTreeComponent() {
    return myLayoutTreeComponent;
  }

  public void updateOutputPath(@NotNull String oldArtifactName, @NotNull final String newArtifactName) {
    final String oldDefaultPath = ArtifactUtil.getDefaultArtifactOutputPath(oldArtifactName, myProject);
    if (Comparing.equal(oldDefaultPath, getConfiguredOutputPath())) {
      setOutputPath(ArtifactUtil.getDefaultArtifactOutputPath(newArtifactName, myProject));
    }
    final CompositePackagingElement<?> root = getRootElement();
    if (root instanceof ArchivePackagingElement) {
      String oldFileName = ArtifactUtil.suggestArtifactFileName(oldArtifactName);
      final String name = ((ArchivePackagingElement)root).getArchiveFileName();
      final String fileName = FileUtil.getNameWithoutExtension(name);
      final String extension = FileUtilRt.getExtension(name);
      if (fileName.equals(oldFileName) && extension.length() > 0) {
        myLayoutTreeComponent.editLayout(
          () -> ((ArchivePackagingElement)getRootElement()).setArchiveFileName(ArtifactUtil.suggestArtifactFileName(newArtifactName) + "." + extension));
        myLayoutTreeComponent.updateRootNode();
      }
    }
  }

  @Override
  public void updateLayoutTree() {
    myLayoutTreeComponent.rebuildTree();
  }

  @Override
  public void putLibraryIntoDefaultLocation(@NotNull Library library) {
    myLayoutTreeComponent.putIntoDefaultLocations(Collections.singletonList(new LibrarySourceItem(library)));
  }

  @Override
  public void putModuleIntoDefaultLocation(@NotNull Module module) {
    myLayoutTreeComponent.putIntoDefaultLocations(Collections.singletonList(new ModuleOutputSourceItem(module)));
  }

  @Override
  public void addToClasspath(final CompositePackagingElement<?> element, List<String> classpath) {
    myLayoutTreeComponent.saveElementProperties();
    ManifestFileConfiguration manifest = myContext.getManifestFile(element, getArtifact().getArtifactType());
    if (manifest == null) {
      final VirtualFile file = ManifestFileUtil.showDialogAndCreateManifest(myContext, element);
      if (file == null) {
        return;
      }

      ManifestFileUtil.addManifestFileToLayout(file.getPath(), myContext, element);
      manifest = myContext.getManifestFile(element, getArtifact().getArtifactType());
    }

    if (manifest != null) {
      manifest.addToClasspath(classpath);
    }
    myLayoutTreeComponent.resetElementProperties();
  }

  public void setArtifactType(ArtifactType artifactType) {
    final ModifiableArtifact modifiableArtifact = myContext.getOrCreateModifiableArtifactModel().getOrCreateModifiableArtifact(myOriginalArtifact);
    modifiableArtifact.setArtifactType(artifactType);

    myPropertiesEditors.removeTabs(myTabbedPane);
    myPropertiesEditors = new ArtifactPropertiesEditors(myContext, myOriginalArtifact, getArtifact());
    myPropertiesEditors.addTabs(myTabbedPane);

    final CompositePackagingElement<?> oldRootElement = getRootElement();
    final CompositePackagingElement<?> newRootElement = artifactType.createRootElement(getArtifact().getName());
    ArtifactUtil.copyChildren(oldRootElement, newRootElement, myProject);
    myLayoutTreeComponent.setRootElement(newRootElement);
  }

  public ArtifactValidationManagerImpl getValidationManager() {
    return myValidationManager;
  }

  private void createUIComponents() {
    myShowContentCheckBox = new ThreeStateCheckBox();
    myShowSpecificContentOptionsButton = new FixedSizeButton(16);
  }

  public String getHelpTopic() {
    final int tab = myTabbedPane.getSelectedIndex();
    if (tab == 0) {
      return "reference.project.structure.artifacts.output";
    }
    String helpId = myPropertiesEditors.getHelpId(myTabbedPane.getSelectedTitle());
    return helpId != null ? helpId : "reference.settingsdialog.project.structure.artifacts";
  }

  private class MyDataProvider implements TypeSafeDataProvider {
    @Override
    public void calcData(DataKey key, DataSink sink) {
      if (ARTIFACTS_EDITOR_KEY.equals(key)) {
        sink.put(ARTIFACTS_EDITOR_KEY, ArtifactEditorImpl.this);
      }
    }
  }

}
