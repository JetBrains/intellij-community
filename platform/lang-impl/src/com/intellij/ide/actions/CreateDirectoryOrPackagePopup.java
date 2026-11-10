// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.actions.CreateDirectoryOrPackageAction.CompletionItem;
import com.intellij.ide.projectView.actions.MarkRootsManager;
import com.intellij.ide.ui.newItemPopup.NewItemPopupUtil;
import com.intellij.ide.ui.newItemPopup.NewItemWithTemplatesPopupPanel;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.SeparatorWithText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.dsl.listCellRenderer.BuilderKt;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.matching.MatchedFragment;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.swing.BorderFactory;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shows the popup of {@link CreateDirectoryOrPackageAction} and creates the directories.
 * The action class does not refer to the popup classes, so the JVM does not load them when it creates the action.
 */
final class CreateDirectoryOrPackagePopup {
  private CreateDirectoryOrPackagePopup() {
  }

  static void show(@NotNull CreateDirectoryOrPackageAction action, @NotNull AnActionEvent event) {
    WriteIntentReadAction.run(() -> {
      final IdeView view = event.getData(LangDataKeys.IDE_VIEW);
      final Project project = event.getData(CommonDataKeys.PROJECT);
      if (view == null || project == null) return;

      final PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);
      if (directory == null) return;

      final CreateGroupHandler validator;
      final String message, title;

      if (action.isPackage(project, Collections.singletonList(directory))) {
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
        String testDirectoryName = event.getData(CreateDirectoryOrPackageAction.TEST_DIRECTORY_NAME_KEY);
        if (testDirectoryName != null && validator.checkInput(testDirectoryName) && validator.canClose(testDirectoryName)) {
          consumer.accept(Collections.singletonList(validator.getCreatedElement()));
          return;
        }
      }

      createLightWeightPopup(action, project, title, initialText, directory, validator, consumer).showCenteredInCurrentWindow(project);
    });
  }

  private static JBPopup createLightWeightPopup(@NotNull CreateDirectoryOrPackageAction action,
                                                @Nullable Project project,
                                                @NlsContexts.PopupTitle String title,
                                                String initialText,
                                                @NotNull PsiDirectory directory,
                                                CreateGroupHandler validator,
                                                Consumer<? super List<PsiElement>> consumer) {
    List<CompletionItem> variants = action.collectSuggestedDirectories(directory);
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

  private static @Nullable List<PsiElement> createDirectories(List<? extends Pair<String, JpsModuleSourceRootType<?>>> toCreate,
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
          ContentEntry entry = MarkRootsManager.findContentEntry(model, file);
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

  private static final class DirectoriesWithCompletionPopupPanel extends NewItemWithTemplatesPopupPanel<CompletionItem> {
    private static final SimpleTextAttributes MATCHED = new SimpleTextAttributes(UIUtil.getListBackground(),
                                                                                 UIUtil.getListForeground(),
                                                                                 null,
                                                                                 SimpleTextAttributes.STYLE_SEARCH_MATCH);
    private MinusculeMatcher currentMatcher = null;
    private boolean locked = false;

    private DirectoriesWithCompletionPopupPanel(@NotNull List<CompletionItem> items) {
      super(items, BuilderKt.textListCellRenderer("", item -> item.displayText), true);
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
            List<MatchedFragment> ranges = currentMatcher == null ? List.of() : currentMatcher.match(text);
            if (ranges != null) {
              List<@NotNull TextRange> colored = ContainerUtil.map(ranges, f -> TextRange.create(f.getStartOffset(), f.getEndOffset()));
              SpeedSearchUtil.appendColoredFragments(this, text, colored, SimpleTextAttributes.REGULAR_ATTRIBUTES, MATCHED);
            }
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
