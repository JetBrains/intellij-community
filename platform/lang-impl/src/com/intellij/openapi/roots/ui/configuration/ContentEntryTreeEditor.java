// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.actions.NewFolderAction;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.options.newEditor.AbstractEditor;
import com.intellij.openapi.options.newEditor.SettingsDialog;
import com.intellij.openapi.options.newEditor.SingleSettingEditor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction;
import com.intellij.openapi.roots.ui.configuration.actions.ToggleExcludedStateAction;
import com.intellij.openapi.roots.ui.configuration.actions.ToggleSourcesStateAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DialogWrapperDialog;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class ContentEntryTreeEditor {
  private final Project myProject;
  private final List<ModuleSourceRootEditHandler<?>> myEditHandlers;
  protected final Tree myTree;
  private FileSystemTreeImpl myFileSystemTree;
  private final JComponent myComponent;
  protected final DefaultActionGroup myEditingActionsGroup;
  private ContentEntryEditor myContentEntryEditor;
  private final MyContentEntryEditorListener myContentEntryEditorListener = new MyContentEntryEditorListener();
  private final FileChooserDescriptor myDescriptor;
  private final JTextField myExcludePatternsField;

  public ContentEntryTreeEditor(Project project, List<ModuleSourceRootEditHandler<?>> editHandlers) {
    myProject = project;
    myEditHandlers = editHandlers;
    myTree = new Tree();
    myTree.setRootVisible(true);
    myTree.setShowsRootHandles(true);

    myEditingActionsGroup = new DefaultActionGroup();

    TreeUtil.installActions(myTree);
    TreeUIHelper.getInstance().installTreeSpeedSearch(myTree);

    JPanel excludePatternsPanel = new JPanel(new GridBagLayout());
    excludePatternsPanel.setBorder(JBUI.Borders.empty(5));
    GridBag gridBag = new GridBag().setDefaultWeightX(1, 1.0).setDefaultPaddingX(JBUIScale.scale(5));
    JLabel myExcludePatternsLabel = new JLabel(ProjectBundle.message("module.paths.exclude.patterns"));
    excludePatternsPanel.add(myExcludePatternsLabel, gridBag.nextLine().next());
    myExcludePatternsField = new JTextField();
    myExcludePatternsLabel.setLabelFor(myExcludePatternsField);
    myExcludePatternsField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        if (myContentEntryEditor != null) {
          ContentEntry entry = myContentEntryEditor.getContentEntry();
          if (entry != null) {
            List<String> patterns = StringUtil.split(myExcludePatternsField.getText().trim(), ";");
            if (!patterns.equals(entry.getExcludePatterns())) {
              entry.setExcludePatterns(patterns);
            }
          }
        }
      }
    });
    excludePatternsPanel.add(myExcludePatternsField, gridBag.next().fillCellHorizontally());
    JBLabel excludePatternsLegendLabel =
      new JBLabel(XmlStringUtil.wrapInHtml(ProjectBundle.message("label.content.entry.separate.name.patterns")));
    excludePatternsLegendLabel.setForeground(JBColor.GRAY);
    excludePatternsPanel.add(excludePatternsLegendLabel, gridBag.nextLine().next().next().fillCellHorizontally());
    JPanel treePanel = new JPanel(new BorderLayout());
    treePanel.add(ScrollPaneFactory.createScrollPane(myTree, true), BorderLayout.CENTER);
    treePanel.add(excludePatternsPanel, BorderLayout.SOUTH);
    myComponent = UiDataProvider.wrapComponent(treePanel, sink -> {
      sink.set(FileSystemTree.DATA_KEY, myFileSystemTree);
      // fix SelectInProjectViewAction if the virtual files are moved into BGT_DATA_PROVIDER
      sink.set(CommonDataKeys.VIRTUAL_FILE_ARRAY, myFileSystemTree == null ? null : myFileSystemTree.getSelectedFiles());
    });
    myComponent.setVisible(false);
    myDescriptor = FileChooserDescriptorFactory.createMultipleFoldersDescriptor();
    myDescriptor.setShowFileSystemRoots(false);
  }

  protected void createEditingActions() {
    for (final ModuleSourceRootEditHandler<?> editor : myEditHandlers) {
      ToggleSourcesStateAction action = new ToggleSourcesStateAction(myTree, this, editor);
      CustomShortcutSet shortcutSet = editor.getMarkRootShortcutSet();
      if (shortcutSet != null) {
        action.registerCustomShortcutSet(shortcutSet, myTree);
      }
      myEditingActionsGroup.add(action);
    }

    setupExcludedAction();
  }

  protected List<ModuleSourceRootEditHandler<?>> getEditHandlers() {
    return myEditHandlers;
  }

  protected TreeCellRenderer getContentEntryCellRenderer(@NotNull ContentEntry contentEntry) {
    return new ContentEntryTreeCellRenderer(this, contentEntry, myEditHandlers);
  }

  /**
   * @param contentEntryEditor : null means to clear the editor
   */
  public void setContentEntryEditor(final ContentEntryEditor contentEntryEditor) {
    if (myContentEntryEditor != null && myContentEntryEditor.equals(contentEntryEditor)) {
      return;
    }
    if (myFileSystemTree != null) {
      Disposer.dispose(myFileSystemTree);
      myFileSystemTree = null;
    }
    if (myContentEntryEditor != null) {
      myContentEntryEditor.removeContentEntryEditorListener(myContentEntryEditorListener);
      myContentEntryEditor = null;
    }
    if (contentEntryEditor == null) {
      myComponent.setVisible(false);
      if (myFileSystemTree != null) {
        Disposer.dispose(myFileSystemTree);
      }
      return;
    }
    myComponent.setVisible(true);
    myContentEntryEditor = contentEntryEditor;
    myContentEntryEditor.addContentEntryEditorListener(myContentEntryEditorListener);

    final ContentEntry entry = contentEntryEditor.getContentEntry();
    assert entry != null : contentEntryEditor;
    final VirtualFile file = entry.getFile();
    if (file != null) {
      myDescriptor.setRoots(file);
    }
    else {
      String path = VfsUtilCore.urlToPath(entry.getUrl());
      myDescriptor.setTitle(FileUtil.toSystemDependentName(path));
    }
    myExcludePatternsField.setText(StringUtil.join(entry.getExcludePatterns(), ";"));

    final Runnable init = () -> {
      myFileSystemTree.updateTree();
      myFileSystemTree.select(file, null);
    };

    myFileSystemTree = new FileSystemTreeImpl(myProject, myDescriptor, myTree, getContentEntryCellRenderer(entry), init, null);
    myFileSystemTree.showHiddens(true);
    Disposer.register(myProject, myFileSystemTree);

    final NewFolderAction newFolderAction = new MyNewFolderAction();
    final DefaultActionGroup mousePopupGroup = new DefaultActionGroup();

    final AnAction navigateAction = new SelectInProjectViewAction();
    navigateAction.registerCustomShortcutSet(myFileSystemTree.getTree(), null);

    mousePopupGroup.add(myEditingActionsGroup);
    mousePopupGroup.addSeparator();
    mousePopupGroup.add(newFolderAction);
    mousePopupGroup.addSeparator();
    mousePopupGroup.add(navigateAction);
    myFileSystemTree.registerMouseListener(mousePopupGroup);
  }

  public @Nullable ContentEntryEditor getContentEntryEditor() {
    return myContentEntryEditor;
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  public JComponent createComponent() {
    createEditingActions();
    return myComponent;
  }

  public void select(VirtualFile file) {
    if (myFileSystemTree != null) {
      myFileSystemTree.select(file, null);
    }
  }

  public void requestFocus() {
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myTree, true));
  }

  public void update() {
    if (myFileSystemTree != null) {
      ContentEntry entry = myContentEntryEditor == null ? null : myContentEntryEditor.getContentEntry();
      if (entry != null) {
        myFileSystemTree.getTree().setCellRenderer(getContentEntryCellRenderer(entry));
      }
      myFileSystemTree.updateTree();
    }
  }

  private final class MyContentEntryEditorListener extends ContentEntryEditorListenerAdapter {
    @Override
    public void sourceFolderAdded(@NotNull ContentEntryEditor editor, SourceFolder folder) {
      update();
    }

    @Override
    public void sourceFolderRemoved(@NotNull ContentEntryEditor editor, VirtualFile file) {
      update();
    }

    @Override
    public void folderExcluded(@NotNull ContentEntryEditor editor, VirtualFile file) {
      update();
    }

    @Override
    public void folderIncluded(@NotNull ContentEntryEditor editor, String fileUrl) {
      update();
    }

    @Override
    public void sourceRootPropertiesChanged(@NotNull ContentEntryEditor editor, @NotNull SourceFolder folder) {
      update();
    }
  }

  private static final class MyNewFolderAction extends NewFolderAction implements CustomComponentAction {
    private MyNewFolderAction() {
      super(ActionsBundle.message("action.FileChooser.NewFolder.text"),
            ActionsBundle.message("action.FileChooser.NewFolder.description"),
            AllIcons.Actions.NewFolder);
    }

    @Override
    public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
      return IconWithTextAction.createCustomComponentImpl(this, presentation, place);
    }
  }

  private static final class SelectInProjectViewAction extends DumbAwareAction {
    private SelectInProjectViewAction() {
      super(ActionsBundle.messagePointer("action.SelectInProjectView.text"));
      copyShortcutFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      VirtualFile[] virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
      if (project == null || ProjectView.getInstance(project) == null) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      Component component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
      DialogWrapper dialogWrapper = getDialogWrapperFor(component);
      Configurable singleConfigurable = getSingleConfigurable(dialogWrapper);
      if (singleConfigurable == null && !ModalityState.current().accepts(ModalityState.nonModal())) {
        e.getPresentation().setEnabledAndVisible(false); // we can't reliably close the dialog
        return;
      }

      e.getPresentation().setEnabled(!ArrayUtil.isEmpty(virtualFiles));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      Component component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
      VirtualFile[] virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
      if (project == null || ArrayUtil.isEmpty(virtualFiles)) return;

      closeSettingsWindow(component);

      ProjectViewSelectInTarget.select(project, null, ProjectViewPane.ID, null, virtualFiles[0], true);
    }

    private static void closeSettingsWindow(@Nullable Component component) {
      DialogWrapper dialogWrapper = getDialogWrapperFor(component);
      if (dialogWrapper == null) return;

      Configurable singleConfigurable = getSingleConfigurable(dialogWrapper);
      if (singleConfigurable == null) return;

      if (singleConfigurable.isModified()) {
        boolean proceed = MessageDialogBuilder.yesNo(ProjectBundle.message("project.structure.unsaved.on.navigation.title"),
                                                     ProjectBundle.message("project.structure.unsaved.on.navigation.message"))
          .yesText(ProjectBundle.message("project.structure.unsaved.on.navigation.discard.action"))
          .noText(CommonBundle.getCancelButtonText())
          .ask(component);
        if (!proceed) return;
      }
      dialogWrapper.doCancelAction();
    }

    private static @Nullable DialogWrapper getDialogWrapperFor(@Nullable Component component) {
      Window window = UIUtil.getWindow(component);
      if (window instanceof DialogWrapperDialog wrapper) {
        return wrapper.getDialogWrapper();
      }
      return null;
    }

    private static @Nullable Configurable getSingleConfigurable(@Nullable DialogWrapper dialogWrapper) {
      if (dialogWrapper instanceof SingleConfigurableEditor settingsDialog) {
        return settingsDialog.getConfigurable();
      }
      if (dialogWrapper instanceof SettingsDialog settingsDialog) {
        AbstractEditor editor = settingsDialog.getEditor();
        if (editor instanceof SingleSettingEditor settingEditor) {
          return settingEditor.getConfigurable();
        }
      }
      return null;
    }
  }

  public DefaultActionGroup getEditingActionsGroup() {
    return myEditingActionsGroup;
  }

  protected void setupExcludedAction() {
    ToggleExcludedStateAction toggleExcludedAction = new ToggleExcludedStateAction(myTree, this);
    myEditingActionsGroup.add(toggleExcludedAction);
    toggleExcludedAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.ALT_MASK)),
                                                   myTree);
  }
}
