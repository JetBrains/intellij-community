// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.actionsOnSave.ActionOnSaveComment;
import com.intellij.ide.actionsOnSave.ActionOnSaveContext;
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.DropDownLink;
import com.intellij.ui.components.JBScrollPane;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.util.*;

@ApiStatus.Internal
public abstract class FormatOnSaveActionInfoBase<Options extends FormatOnSaveOptionsBase<?>> extends ActionOnSaveInfo {

  private final @NotNull @NlsContexts.Checkbox String myActionOnSaveName;
  private final @NotNull Options myCurrentUiState;

  FormatOnSaveActionInfoBase(@NotNull ActionOnSaveContext context,
                             @NotNull @NlsContexts.Checkbox String actionOnSaveName,
                             @NotNull Key<Options> currentUiStateKey) {
    super(context);
    myActionOnSaveName = actionOnSaveName;

    Options options = getContext().getUserData(currentUiStateKey);
    if (options == null) {
      //noinspection AbstractMethodCallInConstructor
      Options storedState = getOptionsFromStoredState();
      //noinspection unchecked
      options = (Options)storedState.clone();
      getContext().putUserData(currentUiStateKey, options);
    }

    myCurrentUiState = options;
  }

  protected abstract @NotNull Options getOptionsFromStoredState();

  @NotNull Options getCurrentUiState() {
    return myCurrentUiState;
  }

  @Override
  public final @NotNull @NlsContexts.Checkbox String getActionOnSaveName() {
    return myActionOnSaveName;
  }

  @Override
  public final boolean isActionOnSaveEnabled() {
    return myCurrentUiState.isRunOnSaveEnabled();
  }

  @Override
  public final void setActionOnSaveEnabled(boolean enabled) {
    myCurrentUiState.setRunOnSaveEnabled(enabled);
  }

  @Override
  public @Nullable ActionOnSaveComment getComment() {
    if (myCurrentUiState.isRunOnSaveEnabled() &&
        !myCurrentUiState.isAllFileTypesSelected() &&
        myCurrentUiState.getSelectedFileTypes().isEmpty()) {
      return ActionOnSaveComment.warning(CodeInsightBundle.message("actions.on.save.page.warning.no.file.types.selected"));
    }

    return null;
  }

  @Override
  public @NotNull List<? extends DropDownLink<?>> getDropDownLinks() {
    return List.of(createFileTypesDropDownLink());
  }

  @NotNull DropDownLink<String> createFileTypesDropDownLink() {
    Function1<DropDownLink<String>, JBPopup> popupBuilder = link -> createFileTypesPopup(link);

    return new DropDownLink<>(getFileTypesLinkText(), popupBuilder);
  }

  private @NotNull @NlsContexts.LinkLabel String getFileTypesLinkText() {
    if (myCurrentUiState.isAllFileTypesSelected()) {
      return CodeInsightBundle.message("actions.on.save.page.label.all.file.types");
    }

    Set<String> fileTypes = myCurrentUiState.getSelectedFileTypes();

    if (fileTypes.isEmpty()) {
      return CodeInsightBundle.message("actions.on.save.page.label.select.file.types");
    }

    String fileTypeName = fileTypes.iterator().next();
    FileType fileType = FileTypeRegistry.getInstance().findFileTypeByName(fileTypeName);
    String presentableFileType = fileType != null ? fileType.getDescription() : fileTypeName;

    if (fileTypes.size() == 1) {
      return CodeInsightBundle.message("actions.on.save.page.label.one.file.type.selected", presentableFileType);
    }

    return CodeInsightBundle.message("actions.on.save.page.label.many.file.types.selected", presentableFileType, fileTypes.size() - 1);
  }

  private @NotNull JBPopup createFileTypesPopup(@NotNull DropDownLink<String> link) {
    CheckedTreeNode root = new CheckedTreeNode(CodeInsightBundle.message("actions.on.save.page.label.all.file.types"));

    SortedSet<FileType> result = new TreeSet<>(Comparator.comparing(FileType::getDescription, String.CASE_INSENSITIVE_ORDER));
    addApplicableFileTypes(result);

    for (FileType fileType : result) {
      root.add(new CheckedTreeNode(fileType));
    }

    CheckboxTree tree = createFileTypesCheckboxTree(root);

    return JBPopupFactory.getInstance().createComponentPopupBuilder(new JBScrollPane(tree), tree)
      .setRequestFocus(true)
      .addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          onFileTypePopupClosed(link, root);
        }
      })
      .createPopup();
  }

  protected abstract void addApplicableFileTypes(@NotNull Collection<? super FileType> result);

  private @NotNull CheckboxTree createFileTypesCheckboxTree(@NotNull CheckedTreeNode root) {
    CheckboxTree tree = new CheckboxTree(createFileTypesRenderer(), root) {
      @Override
      protected void installSpeedSearch() {
        TreeSpeedSearch.installOn(this, false, path -> {
          final CheckedTreeNode node = (CheckedTreeNode)path.getLastPathComponent();
          final Object userObject = node.getUserObject();
          if (userObject instanceof FileType) {
            return ((FileType)userObject).getDescription();
          }
          return userObject.toString();
        });
      }
    };

    tree.setRootVisible(true);
    tree.setSelectionRow(0);

    resetTree(root);

    return tree;
  }

  private void resetTree(@NotNull CheckedTreeNode root) {
    if (myCurrentUiState.isAllFileTypesSelected()) {
      root.setChecked(true);
      return;
    }

    root.setChecked(false);

    Enumeration<TreeNode> fileTypesEnum = root.children();
    while (fileTypesEnum.hasMoreElements()) {
      CheckedTreeNode node = (CheckedTreeNode)fileTypesEnum.nextElement();
      FileType fileType = (FileType)node.getUserObject();
      node.setChecked(myCurrentUiState.isFileTypeSelected(fileType));
    }
  }

  private void onFileTypePopupClosed(@NotNull DropDownLink<String> link, @NotNull CheckedTreeNode root) {
    Options options = myCurrentUiState;

    if (root.isChecked()) {
      options.setRunForAllFileTypes();
    }
    else {
      Collection<FileType> fileTypes = new ArrayList<>();
      Enumeration<TreeNode> fileTypesEnum = root.children();
      while (fileTypesEnum.hasMoreElements()) {
        CheckedTreeNode node = (CheckedTreeNode)fileTypesEnum.nextElement();
        if (node.isChecked()) {
          fileTypes.add((FileType)node.getUserObject());
        }
      }

      options.setRunForSelectedFileTypes(fileTypes);
    }

    link.setText(getFileTypesLinkText());
  }

  private static @NotNull CheckboxTree.CheckboxTreeCellRenderer createFileTypesRenderer() {
    return new CheckboxTree.CheckboxTreeCellRenderer() {
      @Override
      public void customizeRenderer(JTree t, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean focus) {
        if (!(value instanceof CheckedTreeNode node)) return;

        final Object userObject = node.getUserObject();

        if (userObject instanceof String) {
          getTextRenderer().append((String)userObject);
        }
        if (userObject instanceof FileType) {
          getTextRenderer().setIcon(((FileType)userObject).getIcon());
          getTextRenderer().append(((FileType)userObject).getDescription());
        }
      }
    };
  }

  @Override
  protected final boolean isModified() {
    return !getOptionsFromStoredState().equals(myCurrentUiState);
  }
}
