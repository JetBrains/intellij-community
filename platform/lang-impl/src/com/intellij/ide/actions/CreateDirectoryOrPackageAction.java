// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.projectView.actions.MarkRootActionBase;
import com.intellij.ide.ui.newItemPopup.NewItemPopupUtil;
import com.intellij.ide.ui.newItemPopup.NewItemWithTemplatesPopupPanel;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.ui.configuration.ModuleSourceRootEditHandler;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.ui.*;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class CreateDirectoryOrPackageAction extends AnAction implements DumbAware {
  public static final ExtensionPointName<CreateDirectoryCompletionContributor> EP = new ExtensionPointName<>("com.intellij.createDirectoryCompletionContributor");

  @TestOnly
  public static final DataKey<String> TEST_DIRECTORY_NAME_KEY = DataKey.create("CreateDirectoryOrPackageAction.testName");

  public CreateDirectoryOrPackageAction() {
    super(IdeBundle.messagePointer("action.create.new.directory.or.package"),
          IdeBundle.messagePointer("action.create.new.directory.or.package"), null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final IdeView view = event.getData(LangDataKeys.IDE_VIEW);
    final Project project = event.getData(CommonDataKeys.PROJECT);
    if (view == null || project == null) return;

    final PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);
    if (directory == null) return;

    final CreateGroupHandler validator;
    final String message, title;

    if (PsiDirectoryFactory.getInstance(project).isPackage(directory)) {
      validator = new CreatePackageHandler(project, directory);
      message = IdeBundle.message("prompt.enter.new.package.name");
      title = IdeBundle.message("title.new.package");
    }
    else {
      validator = new CreateDirectoryHandler(project, directory);
      message = IdeBundle.message("prompt.enter.new.directory.name");
      title = IdeBundle.message("title.new.directory");
    }

    String initialText = validator.getInitialText();
    Consumer<List<PsiElement>> consumer = elements -> {
      // we don't have API for multi-selection in the views,
      // so let's at least make sure the created elements are visible, and the first one is selected
      for (PsiElement element : ContainerUtil.iterateBackward(elements)) {
        view.selectElement(element);
      }
    };

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      @SuppressWarnings("TestOnlyProblems")
      String testDirectoryName = event.getData(TEST_DIRECTORY_NAME_KEY);
      if (testDirectoryName != null && validator.checkInput(testDirectoryName) && validator.canClose(testDirectoryName)) {
        consumer.accept(Collections.singletonList(validator.getCreatedElement()));
        return;
      }
    }

    if (Experiments.getInstance().isFeatureEnabled("show.create.new.element.in.popup")) {
      createLightWeightPopup(project, title, initialText, directory, validator, consumer).showCenteredInCurrentWindow(project);
    }
    else {
      Messages.showInputDialog(project, message, title, null, initialText, validator, TextRange.from(initialText.length(), 0));
      consumer.accept(Collections.singletonList(validator.getCreatedElement()));
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    IdeView view = event.getData(LangDataKeys.IDE_VIEW);
    if (view == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    final PsiDirectory[] directories = view.getDirectories();
    if (directories.length == 0) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    presentation.setEnabledAndVisible(true);

    boolean isPackage = false;
    final PsiDirectoryFactory factory = PsiDirectoryFactory.getInstance(project);
    for (PsiDirectory directory : directories) {
      if (factory.isPackage(directory)) {
        isPackage = true;
        break;
      }
    }

    if (isPackage) {
      presentation.setText(IdeBundle.messagePointer("action.package"));
      presentation.setIcon(AllIcons.Nodes.Package);
    }
    else {
      presentation.setText(IdeBundle.messagePointer("action.directory"));
      presentation.setIcon(PlatformIcons.FOLDER_ICON);
    }
  }

  private JBPopup createLightWeightPopup(@Nullable Project project,
                                                @NlsContexts.PopupTitle String title,
                                                String initialText,
                                                @NotNull PsiDirectory directory,
                                                CreateGroupHandler validator,
                                                Consumer<? super List<PsiElement>> consumer) {
    List<CompletionItem> variants = collectSuggestedDirectories(directory);
    DirectoriesWithCompletionPopupPanel contentPanel = new DirectoriesWithCompletionPopupPanel(variants);

    JTextField nameField = contentPanel.getTextField();
    nameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(@NotNull DocumentEvent event) {
        final String text = nameField.getText();
        validator.checkInput(text);
        String errorText = validator.getErrorText(text);
        String warningText = validator.getWarningText(text);
        if (errorText != null) {
          contentPanel.setError(errorText);
        }
        else if (warningText != null) {
          contentPanel.setWarning(warningText);
        }
        else if (contentPanel.hasError()) {
          contentPanel.setError(null);
        }
      }
    });
    nameField.setText(initialText);
    JBPopup popup = NewItemPopupUtil.createNewItemPopup(title, contentPanel, nameField);

    contentPanel.setApplyAction(event -> {
      for (CompletionItem it : contentPanel.getSelectedItems()) {
        CreateDirectoryUsageCollector.logCompletionVariantChosen(project, it.contributor.getClass());
      }

      // if there are selected suggestions, we need to create the selected folders (not the path in the text field)
      List<Pair<String, JpsModuleSourceRootType<?>>> toCreate
        = ContainerUtil.map(contentPanel.getSelectedItems(), item -> Pair.create(item.relativePath, item.rootType));

      // when there are no selected suggestions, simply create the directory with the path from the text field
      if (toCreate.isEmpty()) toCreate = Collections.singletonList(Pair.create(nameField.getText(), null));

      List<PsiElement> created = createDirectories(toCreate, validator);
      if (created != null) {
        popup.closeOk(event);
        consumer.accept(created);
      }
      else {
        for (Pair<String, JpsModuleSourceRootType<?>> dir : toCreate) {
          String errorMessage = validator.getErrorText(dir.first);
          if (errorMessage != null) {
            contentPanel.setError(errorMessage);
            break;
          }
        }
      }
    });

    contentPanel.addTemplatesVisibilityListener(visible -> {
      // The re-layout should be delayed since we are in the middle of model changes processing and not all components updated their states
      SwingUtilities.invokeLater(() -> popup.pack(false, true));
    });

    return popup;
  }

  @NotNull
  protected List<CompletionItem> collectSuggestedDirectories(@NotNull PsiDirectory directory) {
    List<CompletionItem> variants = new ArrayList<>();

    VirtualFile vDir = directory.getVirtualFile();
    for (CreateDirectoryCompletionContributor contributor : EP.getExtensionList()) {
      for (CreateDirectoryCompletionContributor.Variant variant : contributor.getVariants(directory)) {
        String relativePath = FileUtil.toSystemIndependentName(variant.path);
        if (FileUtil.isAbsolutePlatformIndependent(relativePath)) {
          // only suggest sub-folders
          if (!FileUtil.isAncestor(vDir.getPath(), relativePath, true)) continue;

          // convert absolute paths to the relative paths
          relativePath = FileUtil.getRelativePath(vDir.getPath(), relativePath, '/');
          if (relativePath == null) continue;
        }

        // only suggested non-existent folders
        if (vDir.findFileByRelativePath(relativePath) != null) continue;

        ModuleSourceRootEditHandler<?> handler =
          variant.rootType != null ? ModuleSourceRootEditHandler.getEditHandler(variant.rootType) : null;

        Icon icon = handler == null ? null : handler.getRootIcon();
        if (icon == null) icon = AllIcons.Nodes.Folder;

        CompletionItem completionItem = new CompletionItem(contributor, relativePath, icon, variant.rootType);
        if (!variants.contains(completionItem)) {
          variants.add(completionItem);
        }
      }
    }

    variants.sort((o1, o2) -> {
      int result = StringUtil.naturalCompare(o1.contributor.getDescription(), o2.contributor.getDescription());
      if (result != 0) return result;
      return StringUtil.naturalCompare(o1.relativePath, o2.relativePath);
    });

    return variants;
  }

  @Nullable
  private static List<PsiElement> createDirectories(List<? extends Pair<String, JpsModuleSourceRootType<?>>> toCreate,
                                                    CreateGroupHandler validator) {
    List<PsiElement> createdDirectories = new ArrayList<>(toCreate.size());

    // first, check that we can create all requested directories
    if (!ContainerUtil.all(toCreate, dir -> !dir.first.isEmpty() && validator.checkInput(dir.first))) return null;

    List<Pair<PsiFileSystemItem, JpsModuleSourceRootType<?>>> toMarkAsRoots = new ArrayList<>(toCreate.size());

    // now create directories one by one
    for (Pair<String, JpsModuleSourceRootType<?>> dir : toCreate) {
      // this call creates a directory
      if (!validator.canClose(dir.first)) return null;
      PsiFileSystemItem element = validator.getCreatedElement();
      if (element == null) continue;

      createdDirectories.add(element);

      // collect folders to mark as source folders later
      JpsModuleSourceRootType<?> rootType = dir.second;
      if (rootType != null) {
        toMarkAsRoots.add(Pair.create(element, rootType));
      }
    }

    if (!toMarkAsRoots.isEmpty()) {
      Project project = toMarkAsRoots.get(0).first.getProject();
      ProjectFileIndex index = ProjectFileIndex.getInstance(project);

      WriteAction.run(() -> ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring(() -> {
        for (Pair<PsiFileSystemItem, JpsModuleSourceRootType<?>> each : toMarkAsRoots) {
          VirtualFile file = each.first.getVirtualFile();
          JpsModuleSourceRootType<?> rootType = each.second;

          // make sure we have a content root for this directory and it's not yet registered as source folder
          Module module = index.getModuleForFile(file);
          if (module == null || index.getContentRootForFile(file) == null || index.isInSourceContent(file)) {
            continue;
          }

          ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
          ContentEntry entry = MarkRootActionBase.findContentEntry(model, file);
          if (entry != null) {
            entry.addSourceFolder(file, rootType);
            model.commit();
          }
          else {
            model.dispose();
          }
        }
      }));
    }

    return createdDirectories;
  }

  protected static final class CompletionItem {
    @NotNull final CreateDirectoryCompletionContributor contributor;

    @NotNull final String relativePath;
    @Nullable final JpsModuleSourceRootType<?> rootType;

    @NlsContexts.ListItem @NotNull final String displayText;
    @Nullable final Icon icon;

    private CompletionItem(@NotNull CreateDirectoryCompletionContributor contributor,
                           @NotNull String relativePath,
                           @Nullable Icon icon,
                           @Nullable JpsModuleSourceRootType<?> rootType) {
      this.contributor = contributor;

      this.relativePath = relativePath;
      this.rootType = rootType;

      this.displayText = FileUtil.toSystemDependentName(relativePath);
      this.icon = icon;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      CompletionItem that = (CompletionItem)o;
      return contributor.equals(that.contributor) &&
             relativePath.equals(that.relativePath) &&
             Objects.equals(rootType, that.rootType) &&
             displayText.equals(that.displayText) &&
             Objects.equals(icon, that.icon);
    }

    @Override
    public int hashCode() {
      return Objects.hash(contributor, relativePath, rootType, displayText, icon);
    }
  }

  private static class DirectoriesWithCompletionPopupPanel extends NewItemWithTemplatesPopupPanel<CompletionItem> {
    final static private SimpleTextAttributes MATCHED = new SimpleTextAttributes(UIUtil.getListBackground(),
                                                                                 UIUtil.getListForeground(),
                                                                                 null,
                                                                                 SimpleTextAttributes.STYLE_SEARCH_MATCH);
    private MinusculeMatcher currentMatcher = null;
    private boolean locked = false;

    protected DirectoriesWithCompletionPopupPanel(@NotNull List<CompletionItem> items) {
      super(items, SimpleListCellRenderer.create("", item -> item.displayText), true);
      setupRenderers();

      // allow multi selection with Shift+Up/Down
      ScrollingUtil.redirectExpandSelection(myTemplatesList, myTextField);

      myTemplatesList.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      myTemplatesList.addListSelectionListener(e -> {
        CompletionItem selected = myTemplatesList.getSelectedValue();
        if (selected != null) {
          locked = true;
          try {
            myTextField.setText(selected.displayText);
          }
          finally {
            locked = false;
          }
        }
      });
      myTextField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          if (!locked) {
            String input = myTextField.getText();
            currentMatcher = NameUtil.buildMatcher("*" + input).build();

            List<CompletionItem> filtered =
              ContainerUtil.filter(items, item -> currentMatcher.matches(item.displayText));

            updateTemplatesList(filtered);
          }
        }
      });

      ListModel<CompletionItem> model = myTemplatesList.getModel();
      model.addListDataListener(new ListDataListener() {
        @Override
        public void intervalAdded(ListDataEvent e) {
          setTemplatesListVisible(model.getSize() > 0);
        }

        @Override
        public void intervalRemoved(ListDataEvent e) {
          setTemplatesListVisible(model.getSize() > 0);
        }

        @Override
        public void contentsChanged(ListDataEvent e) {
          setTemplatesListVisible(model.getSize() > 0);
        }
      });
      setTemplatesListVisible(model.getSize() > 0);
    }

    @NotNull
    List<CompletionItem> getSelectedItems() {
      return myTemplatesList.getSelectedValuesList();
    }

    private void setupRenderers() {
      ColoredListCellRenderer<CompletionItem> itemRenderer =
        new ColoredListCellRenderer<>() {
          @Override
          protected void customizeCellRenderer(@NotNull JList<? extends CompletionItem> list,
                                               @Nullable CompletionItem value,
                                               int index,
                                               boolean selected,
                                               boolean hasFocus) {
            if (!selected) {
              setBackground(UIUtil.getListBackground());
            }

            String text = value == null ? "" : value.displayText;
            FList<TextRange> ranges = currentMatcher == null ? FList.emptyList() : currentMatcher.matchingFragments(text);
            SpeedSearchUtil.appendColoredFragments(this, text, ranges, SimpleTextAttributes.REGULAR_ATTRIBUTES, MATCHED);
            setIcon(value == null ? null : value.icon);
          }
        };
      myTemplatesList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
        Component item = itemRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.setBackground(UIUtil.getListBackground());

        if (index == 0 || value.contributor != list.getModel().getElementAt(index - 1).contributor) {
          SeparatorWithText separator = new SeparatorWithText() {
            @Override
            protected void paintLinePart(Graphics g, int xMin, int xMax, int hGap, int y) {
            }
          };

          separator.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
          int vGap = UIUtil.DEFAULT_VGAP / 2;
          separator.setBorder(BorderFactory.createEmptyBorder(vGap * (index == 0 ? 1 : 2), 0, vGap, 0));

          separator.setCaption(value.contributor.getDescription());
          separator.setCaptionCentered(false);

          wrapperPanel.add(separator, BorderLayout.NORTH);
        }
        wrapperPanel.add(item, BorderLayout.CENTER);
        return wrapperPanel;
      });
    }
  }
}
