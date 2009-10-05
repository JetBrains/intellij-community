package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.impl.TypeSafeDataProviderAdapter;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.artifacts.actions.*;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.LibrarySourceItem;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.SourceItemsTree;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.elements.ArchivePackagingElement;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
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
  private JCheckBox myShowIncludedCheckBox;
  private JPanel myEditorPanel;
  private JPanel myErrorPanelPlace;
  private Splitter mySplitter;
  private final Project myProject;
  private final ComplexElementSubstitutionParameters mySubstitutionParameters = new ComplexElementSubstitutionParameters();
  private final EventDispatcher<ArtifactEditorListener> myDispatcher = EventDispatcher.create(ArtifactEditorListener.class);
  private final ArtifactEditorContextImpl myContext;
  private SourceItemsTree mySourceItemsTree;
  private final Artifact myOriginalArtifact;
  private final LayoutTreeComponent myLayoutTreeComponent;
  private TabbedPaneWrapper myTabbedPane;
  private ArtifactPropertiesEditors myPropertiesEditors;
  private ArtifactValidationManagerImpl myValidationManager;

  public ArtifactEditorImpl(final ArtifactsStructureConfigurableContext context, Artifact artifact) {
    myContext = new ArtifactEditorContextImpl(context, this);
    myOriginalArtifact = artifact;
    myProject = context.getProject();
    mySourceItemsTree = new SourceItemsTree(myContext, this);
    myLayoutTreeComponent = new LayoutTreeComponent(this, mySubstitutionParameters, myContext, myOriginalArtifact);
    myPropertiesEditors = new ArtifactPropertiesEditors(myContext, myOriginalArtifact);
    Disposer.register(this, mySourceItemsTree);
    Disposer.register(this, myLayoutTreeComponent);
    myBuildOnMakeCheckBox.setSelected(artifact.isBuildOnMake());
    final String outputPath = artifact.getOutputPath();
    myOutputDirectoryField.addBrowseFolderListener(CompilerBundle.message("dialog.title.output.directory.for.artifact"),
                                                   CompilerBundle.message("chooser.description.select.output.directory.for.0.artifact",
                                                                          getArtifact().getName()), myProject,
                                                   FileChooserDescriptorFactory.createSingleFolderDescriptor());
    setOutputPath(outputPath);
    myValidationManager = new ArtifactValidationManagerImpl(this);
    myContext.setValidationManager(myValidationManager);
  }

  private void setOutputPath(@Nullable String outputPath) {
    myOutputDirectoryField.setText(outputPath != null ? FileUtil.toSystemDependentName(outputPath) : null);
  }

  public void apply() {
    final ModifiableArtifact modifiableArtifact = myContext.getModifiableArtifactModel().getOrCreateModifiableArtifact(myOriginalArtifact);
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

  public ArtifactEditorContextImpl getContext() {
    return myContext;
  }

  public void removeListener(@NotNull final ArtifactEditorListener listener) {
    myDispatcher.removeListener(listener);
  }

  public Artifact getArtifact() {
    return myContext.getArtifactModel().getArtifactByOriginal(myOriginalArtifact);
  }

  public CompositePackagingElement<?> getRootElement() {
    return myLayoutTreeComponent.getRootElement();
  }

  public void rebuildTries() {
    myLayoutTreeComponent.rebuildTree();
    mySourceItemsTree.rebuildTree();
  }

  public void queueValidation() {
    myValidationManager.queueValidation();
  }

  public JComponent createMainComponent() {
    mySourceItemsTree.initTree();
    myLayoutTreeComponent.initTree();
    myMainPanel.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, new TypeSafeDataProviderAdapter(new MyDataProvider()));

    myErrorPanelPlace.add(myValidationManager.getMainErrorPanel(), BorderLayout.CENTER);

    mySplitter = new Splitter(false);
    final JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.add(myLayoutTreeComponent.getTreePanel(), BorderLayout.CENTER);
    final Border border = BorderFactory.createEmptyBorder(3, 3, 3, 3);
    leftPanel.setBorder(border);
    mySplitter.setFirstComponent(leftPanel);

    final JPanel rightPanel = new JPanel(new BorderLayout());
    final JPanel rightTopPanel = new JPanel(new BorderLayout());
    rightTopPanel.add(new JLabel("Available Elements (drag'n'drop to layout tree)"), BorderLayout.SOUTH);
    rightPanel.add(rightTopPanel, BorderLayout.NORTH);
    rightPanel.add(ScrollPaneFactory.createScrollPane(mySourceItemsTree.getTree()), BorderLayout.CENTER);
    rightPanel.add(new JPanel(), BorderLayout.SOUTH);
    rightPanel.setBorder(border);
    mySplitter.setSecondComponent(rightPanel);


    myShowIncludedCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myShowIncludedCheckBox.isSelected()) {
          mySubstitutionParameters.setSubstituteAll();
        }
        else {
          mySubstitutionParameters.setSubstituteNone();
        }
        rebuildTries();
      }
    });

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, createToolbarActionGroup(), true);
    leftPanel.add(toolbar.getComponent(), BorderLayout.NORTH);
    rightTopPanel.setPreferredSize(new Dimension(-1, toolbar.getComponent().getPreferredSize().height));

    myTabbedPane = new TabbedPaneWrapper(this);
    myTabbedPane.addTab("Output Layout", mySplitter);
    myPropertiesEditors.addTabs(myTabbedPane);
    myEditorPanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);

    PopupHandler.installPopupHandler(myLayoutTreeComponent.getLayoutTree(), createPopupActionGroup(), ActionPlaces.UNKNOWN, ActionManager.getInstance());
    TreeToolTipHandler.install(myLayoutTreeComponent.getLayoutTree());
    ToolTipManager.sharedInstance().registerComponent(myLayoutTreeComponent.getLayoutTree());
    rebuildTries();
    return getMainComponent();
  }

  private DefaultActionGroup createToolbarActionGroup() {
    final DefaultActionGroup toolbarActionGroup = new DefaultActionGroup();

    final List<AnAction> createActions = new ArrayList<AnAction>();
    AddCompositeElementActionGroup.addCompositeCreateActions(createActions, this);
    for (AnAction createAction : createActions) {
      toolbarActionGroup.add(createAction);
    }

    toolbarActionGroup.add(createAddAction(false));
    toolbarActionGroup.add(new RemovePackagingElementAction(this));
    toolbarActionGroup.add(new SortElementsToggleAction(this.getLayoutTreeComponent()));
    toolbarActionGroup.add(new MoveElementAction(myLayoutTreeComponent, "Move Up", "", IconLoader.getIcon("/actions/moveUp.png"), -1));
    toolbarActionGroup.add(new MoveElementAction(myLayoutTreeComponent, "Move Down", "", IconLoader.getIcon("/actions/moveDown.png"), 1));
    return toolbarActionGroup;
  }

  private DefaultActionGroup createPopupActionGroup() {
    DefaultActionGroup popupActionGroup = new DefaultActionGroup();
    final List<AnAction> createActions = new ArrayList<AnAction>();
    AddCompositeElementActionGroup.addCompositeCreateActions(createActions, this);
    for (AnAction createAction : createActions) {
      popupActionGroup.add(createAction);
    }
    popupActionGroup.add(createAddAction(true));
    final RemovePackagingElementAction removeAction = new RemovePackagingElementAction(this);
    final LayoutTree tree = myLayoutTreeComponent.getLayoutTree();
    removeAction.registerCustomShortcutSet(CommonShortcuts.DELETE, tree);
    popupActionGroup.add(removeAction);
    popupActionGroup.add(new ExtractArtifactAction(this));
    popupActionGroup.add(new InlineArtifactAction(this));
    popupActionGroup.add(new RenamePackagingElementAction(this));
    popupActionGroup.add(Separator.getInstance());
    popupActionGroup.add(new HideContentAction(this));
    popupActionGroup.add(new ArtifactEditorNavigateAction(myLayoutTreeComponent));
    popupActionGroup.add(new ArtifactEditorFindUsagesAction(myLayoutTreeComponent, myProject));

    popupActionGroup.add(Separator.getInstance());
    CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    DefaultTreeExpander treeExpander = new DefaultTreeExpander(tree);
    popupActionGroup.add(actionsManager.createExpandAllAction(treeExpander, tree));
    popupActionGroup.add(actionsManager.createCollapseAllAction(treeExpander, tree));
    return popupActionGroup;
  }

  public ComplexElementSubstitutionParameters getSubstitutionParameters() {
    return mySubstitutionParameters;
  }

  private AddPackagingElementActionGroup createAddAction(boolean popup) {
    return new AddPackagingElementActionGroup(this);
  }

  public JComponent getMainComponent() {
    return myMainPanel;
  }

  public void addNewPackagingElement(@NotNull PackagingElementType<?> type) {
    myLayoutTreeComponent.addNewPackagingElement(type);
    mySourceItemsTree.rebuildTree();
  }

  public void removeSelectedElements() {
    myLayoutTreeComponent.removeSelectedElements();
  }

  public boolean isModified() {
    return myBuildOnMakeCheckBox.isSelected() != myOriginalArtifact.isBuildOnMake()
        || !Comparing.equal(getConfiguredOutputPath(), myOriginalArtifact.getOutputPath())
        || myPropertiesEditors.isModified()
        || myLayoutTreeComponent.isPropertiesModified();
  }

  public void dispose() {
  }

  public LayoutTreeComponent getLayoutTreeComponent() {
    return myLayoutTreeComponent;
  }

  public void updateOutputPath(@NotNull String oldArtifactName, @NotNull String newArtifactName) {
    final String oldDefaultPath = ArtifactUtil.getDefaultArtifactOutputPath(oldArtifactName, myProject);
    if (Comparing.equal(oldDefaultPath, getConfiguredOutputPath())) {
      setOutputPath(ArtifactUtil.getDefaultArtifactOutputPath(newArtifactName, myProject));
      final CompositePackagingElement<?> root = getRootElement();
      if (root instanceof ArchivePackagingElement) {
        final String name = ((ArchivePackagingElement)root).getArchiveFileName();
        final String fileName = FileUtil.getNameWithoutExtension(name);
        final String extension = FileUtil.getExtension(name);
        if (fileName.equals(oldArtifactName) && extension.length() > 0) {
          myLayoutTreeComponent.ensureRootIsWritable();
          ((ArchivePackagingElement)getRootElement()).setArchiveFileName(newArtifactName + "." + extension);
          myLayoutTreeComponent.updateTreeNodesPresentation();
        }
      }
    }
  }

  public void putLibraryIntoDefaultLocation(@NotNull Library library) {
    myLayoutTreeComponent.putIntoDefaultLocations(Collections.singletonList(new LibrarySourceItem(library)));
  }

  public void addToClasspath(CompositePackagingElement<?> element, List<String> classpath) {
    myLayoutTreeComponent.saveElementProperties();
    final ManifestFileConfiguration manifest = myContext.getManifestFile(element, getArtifact().getArtifactType());
    manifest.addToClasspath(classpath);
    myLayoutTreeComponent.resetElementProperties();
  }

  private class MyDataProvider implements TypeSafeDataProvider {
    public void calcData(DataKey key, DataSink sink) {
      if (ARTIFACTS_EDITOR_KEY.equals(key)) {
        sink.put(ARTIFACTS_EDITOR_KEY, ArtifactEditorImpl.this);
      }
    }
  }

}