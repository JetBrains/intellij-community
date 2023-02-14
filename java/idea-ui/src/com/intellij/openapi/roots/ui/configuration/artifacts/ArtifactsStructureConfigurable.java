// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.CommonBundle;
import com.intellij.ide.JavaUiBundle;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditorListener;
import com.intellij.openapi.roots.ui.configuration.projectRoot.*;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.ui.MasterDetailsState;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.NonEmptyInputValidator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsActions;
import com.intellij.packaging.artifacts.*;
import com.intellij.packaging.elements.ComplexPackagingElementType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.artifacts.InvalidArtifact;
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
import java.util.Collections;
import java.util.List;

public final class ArtifactsStructureConfigurable extends BaseStructureConfigurable {
  private ArtifactsStructureConfigurableContextImpl myPackagingEditorContext;
  private final ArtifactEditorSettings myDefaultSettings = new ArtifactEditorSettings();

  public ArtifactsStructureConfigurable(@NotNull ProjectStructureConfigurable projectStructureConfigurable) {
    super(projectStructureConfigurable, new ArtifactStructureConfigurableState());
    PackagingElementType.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionRemoved(@NotNull PackagingElementType extension, @NotNull PluginDescriptor pluginDescriptor) {
        if (extension instanceof ComplexPackagingElementType && myDefaultSettings.getTypesToShowContent().contains(extension)) {
          List<ComplexPackagingElementType<?>> updated = new ArrayList<>(myDefaultSettings.getTypesToShowContent());
          updated.remove(extension);
          myDefaultSettings.setTypesToShowContent(updated);
        }
      }
    }, this);
  }

  @Override
  protected String getComponentStateKey() {
    return "ArtifactsStructureConfigurable.UI";
  }

  public void init(StructureConfigurableContext context, ModuleStructureConfigurable moduleStructureConfigurable,
                   ProjectLibrariesConfigurable projectLibrariesConfig, GlobalLibrariesConfigurable globalLibrariesConfig) {
    super.init(context);
    myPackagingEditorContext = new ArtifactsStructureConfigurableContextImpl(myContext, myProject, myDefaultSettings, new ArtifactListener() {
      @Override
      public void artifactAdded(@NotNull Artifact artifact) {
        final MyNode node = addArtifactNode(artifact);
        selectNodeInTree(node);
        myContext.getDaemonAnalyzer().queueUpdate(myPackagingEditorContext.getOrCreateArtifactElement(artifact));
      }
    });

    context.getModulesConfigurator().addAllModuleChangeListener(new ModuleEditor.ChangeListener() {
      @Override
      public void moduleStateChanged(ModifiableRootModel moduleRootModel) {
        for (ProjectStructureElement element : getProjectStructureElements()) {
          myContext.getDaemonAnalyzer().queueUpdate(element);
        }
      }
    });

    final ItemsChangeListener listener = new ItemsChangeListener() {
      @Override
      public void itemChanged(@Nullable Object deletedItem) {
        if (deletedItem instanceof Library || deletedItem instanceof Module) {
          onElementDeleted();
        }
      }
    };
    moduleStructureConfigurable.addItemsChangeListener(listener);
    projectLibrariesConfig.addItemsChangeListener(listener);
    globalLibrariesConfig.addItemsChangeListener(listener);

    context.addLibraryEditorListener(new LibraryEditorListener() {
      @Override
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
                                              new PackagingElementProcessor<>() {
                                                @Override
                                                public boolean process(@NotNull LibraryPackagingElement element,
                                                                       @NotNull PackagingElementPath path) {
                                                  return !isResolvedToLibrary(element, library, oldName);
                                                }
                                              }, myPackagingEditorContext, false, artifact.getArtifactType())) {
      return;
    }
    myPackagingEditorContext.editLayout(artifact, () -> {
      final ModifiableArtifact modifiableArtifact = myPackagingEditorContext.getOrCreateModifiableArtifactModel().getOrCreateModifiableArtifact(artifact);
      ArtifactUtil.processPackagingElements(modifiableArtifact, LibraryElementType.LIBRARY_ELEMENT_TYPE, new PackagingElementProcessor<>() {
        @Override
        public boolean process(@NotNull LibraryPackagingElement element, @NotNull PackagingElementPath path) {
          if (isResolvedToLibrary(element, library, oldName)) {
            element.setLibraryName(newName);
          }
          return true;
        }
      }, myPackagingEditorContext, false);
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

  @Override
  protected MasterDetailsState getState() {
    ((ArtifactStructureConfigurableState)myState).setDefaultArtifactSettings(myDefaultSettings.getState());
    return super.getState();
  }

  @Override
  public void loadState(MasterDetailsState object) {
    super.loadState(object);
    myDefaultSettings.loadState(((ArtifactStructureConfigurableState)myState).getDefaultArtifactSettings());
  }

  @Override
  @Nls
  public String getDisplayName() {
    return JavaUiBundle.message("display.name.artifacts");
  }

  @Override
  protected void loadTree() {
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(false);
    for (Artifact artifact : myPackagingEditorContext.getArtifactModel().getAllArtifactsIncludingInvalid()) {
      addArtifactNode(artifact);
    }
  }

  @NotNull
  @Override
  protected Collection<? extends ProjectStructureElement> getProjectStructureElements() {
    final List<ProjectStructureElement> elements = new ArrayList<>();
    for (Artifact artifact : myPackagingEditorContext.getArtifactModel().getAllArtifactsIncludingInvalid()) {
      elements.add(myPackagingEditorContext.getOrCreateArtifactElement(artifact));
    }
    return elements;
  }

  private MyNode addArtifactNode(final Artifact artifact) {
    final NamedConfigurable<Artifact> configurable;
    if (artifact instanceof InvalidArtifact) {
      configurable = new InvalidArtifactConfigurable((InvalidArtifact)artifact, myPackagingEditorContext, TREE_UPDATER);
    }
    else {
      configurable = new ArtifactConfigurable(artifact, myPackagingEditorContext, TREE_UPDATER);
    }
    final MyNode node = new MyNode(configurable);
    addNode(node, myRoot);
    return node;
  }

  @Override
  public void reset() {
    loadComponentState();
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

  @Override
  protected AbstractAddGroup createAddAction() {
    return new AbstractAddGroup(JavaUiBundle.message("add.new.header.text")) {
      @Override
      public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        List<ArtifactType> types = ArtifactType.getAllTypes();
        final AnAction[] actions = new AnAction[types.size()];
        for (int i = 0; i < types.size(); i++) {
          actions[i] = createAddArtifactAction(types.get(i));
        }
        return actions;
      }
    };
  }

  private AnAction createAddArtifactAction(@NotNull final ArtifactType type) {
    final List<? extends ArtifactTemplate> templates = type.getNewArtifactTemplates(myPackagingEditorContext);
    final ArtifactTemplate emptyTemplate = new ArtifactTemplate() {
      @Override
      public @Nls(capitalization = Nls.Capitalization.Title) String getPresentableName() {
        return JavaBundle.message("empty.title");
      }

      @Override
      public NewArtifactConfiguration createArtifact() {
        final String name = "unnamed";
        return new NewArtifactConfiguration(type.createRootElement(name), name, type);
      }
    };

    if (templates.isEmpty()) {
      return new AddArtifactAction(type, emptyTemplate, type.getPresentableName(), type.getIcon());
    }
    final DefaultActionGroup group = DefaultActionGroup.createPopupGroup(() -> type.getPresentableName());
    group.getTemplatePresentation().setIcon(type.getIcon());
    group.add(new AddArtifactAction(type, emptyTemplate, emptyTemplate.getPresentableName(), null));
    group.addSeparator();
    for (ArtifactTemplate template : templates) {
      group.add(new AddArtifactAction(type, template, template.getPresentableName(), null));
    }
    return group;
  }

  private void addArtifact(@NotNull ArtifactType type, @NotNull ArtifactTemplate artifactTemplate) {
    Artifact artifact = ArtifactUtil.addArtifact(myPackagingEditorContext.getOrCreateModifiableArtifactModel(), type, artifactTemplate);
    selectNodeInTree(findNodeByObject(myRoot, artifact));
  }

  @NotNull
  @Override
  protected List<? extends AnAction> createCopyActions(boolean fromPopup) {
    final ArrayList<AnAction> actions = new ArrayList<>();
    actions.add(new CopyArtifactAction());
    return actions;
  }

  @Override
  public void apply() throws ConfigurationException {
    myPackagingEditorContext.saveEditorSettings();
    checkForEmptyAndDuplicatedNames(JavaUiBundle.message("configurable.artifact.prefix"), CommonBundle.getErrorTitle(), ArtifactConfigurableBase.class);
    super.apply();

    myPackagingEditorContext.getManifestFilesInfo().saveManifestFiles();
    final ModifiableArtifactModel modifiableModel = myPackagingEditorContext.getActualModifiableModel();
    if (modifiableModel != null) {
      WriteAction.run(() -> modifiableModel.commit());
    }
    myPackagingEditorContext.resetModifiableModel();
    reloadTreeNodes();
    restoreLastSelection();
  }

  @Override
  public void disposeUIResources() {
    myPackagingEditorContext.saveEditorSettings();
    super.disposeUIResources();
    myPackagingEditorContext.resetModifiableModel();
  }

  @Override
  protected void updateSelection(@Nullable NamedConfigurable configurable) {
    boolean selectionChanged = !Comparing.equal(myCurrentConfigurable, configurable);
    if (selectionChanged && myCurrentConfigurable instanceof ArtifactConfigurable) {
      ArtifactEditorImpl editor = myPackagingEditorContext.getArtifactEditor(((ArtifactConfigurable)myCurrentConfigurable).getArtifact());
      if (editor != null) {
        editor.getLayoutTreeComponent().saveElementProperties();
      }
    }
    super.updateSelection(configurable);
    if (selectionChanged && configurable instanceof ArtifactConfigurable) {
      ArtifactEditorImpl editor = myPackagingEditorContext.getArtifactEditor(((ArtifactConfigurable)configurable).getArtifact());
      if (editor != null) {
        editor.getLayoutTreeComponent().resetElementProperties();
      }
    }
  }

  @Override
  public String getHelpTopic() {
    final String topic = super.getHelpTopic();
    return topic != null ? topic : "reference.settingsdialog.project.structure.artifacts";
  }

  @Override
  protected List<? extends RemoveConfigurableHandler<?>> getRemoveHandlers() {
    return Collections.singletonList(new ArtifactRemoveHandler());
  }

  @Override
  @NotNull
  public String getId() {
    return "project.artifacts";
  }

  @Override
  public void dispose() {
  }

  private class ArtifactRemoveHandler extends RemoveConfigurableHandler<Artifact> {
    ArtifactRemoveHandler() {
      super(ArtifactConfigurableBase.class);
    }

    @Override
    public boolean remove(@NotNull Collection<? extends Artifact> artifacts) {
      for (Artifact artifact : artifacts) {
        myPackagingEditorContext.getOrCreateModifiableArtifactModel().removeArtifact(artifact);
        myContext.getDaemonAnalyzer().removeElement(myPackagingEditorContext.getOrCreateArtifactElement(artifact));
      }
      return true;
    }
  }

  private class AddArtifactAction extends DumbAwareAction {
    private final ArtifactType myType;
    private final ArtifactTemplate myArtifactTemplate;

    AddArtifactAction(@NotNull ArtifactType type, @NotNull ArtifactTemplate artifactTemplate, final @NotNull @NlsActions.ActionText String actionText,
                             final Icon icon) {
      super(actionText, null, icon);
      myType = type;
      myArtifactTemplate = artifactTemplate;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      addArtifact(myType, myArtifactTemplate);
    }
  }

  private final class CopyArtifactAction extends AnAction {
   private CopyArtifactAction() {
      super(CommonBundle.messagePointer("button.copy"), CommonBundle.messagePointer("button.copy"), COPY_ICON);
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
      final Object o = getSelectedObject();
      if (o instanceof Artifact selected) {
        ModifiableArtifactModel artifactModel = myPackagingEditorContext.getOrCreateModifiableArtifactModel();
        String suggestedName = ArtifactUtil.generateUniqueArtifactName(selected.getName(), artifactModel);
        final String newName = Messages.showInputDialog(JavaUiBundle.message("label.enter.artifact.name"),
                                                        JavaUiBundle.message("dialog.title.copy.artifact"),
                                                        COPY_ICON,
                                                        suggestedName,
                                                        new NonEmptyInputValidator());
        if (newName == null) return;

        CompositePackagingElement<?> rootCopy = ArtifactUtil.copyFromRoot(selected.getRootElement(), myProject);
        artifactModel.addArtifact(newName, selected.getArtifactType(), rootCopy);
      }
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      if (myTree.getSelectionPaths() == null || myTree.getSelectionPaths().length != 1) {
        e.getPresentation().setEnabled(false);
      }
      else {
        e.getPresentation().setEnabled(getSelectedObject() instanceof Artifact);
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }
}
