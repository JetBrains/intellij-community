// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.application.options.GeneralCodeStylePanel;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.actions.VcsFacade;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.ide.actionsOnSave.ActionOnSaveComment;
import com.intellij.ide.actionsOnSave.ActionOnSaveContext;
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.DropDownLink;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public class FormatOnSaveActionInfo extends ActionOnSaveInfo {

  private static final Key<FormatOnSaveOptions> OPTIONS_FROM_UI_KEY = Key.create("format.on.save.options");

  public FormatOnSaveActionInfo(@NotNull ActionOnSaveContext context) {
    super(context);
  }

  @Override
  public @NotNull String getActionOnSaveName() {
    return CodeInsightBundle.message("actions.on.save.page.checkbox.reformat.code");
  }

  @Override
  public boolean isActionOnSaveEnabled() {
    FormatOnSaveOptions options = ObjectUtils.notNull(getOptionsObjectStoringUiState(), FormatOnSaveOptions.getInstance(getProject()));
    return options.isFormatOnSaveEnabled();
  }

  @Override
  public void setActionOnSaveEnabled(boolean enabled) {
    FormatOnSaveOptions options = getOrCreateOptionsObjectStoringUiState();
    options.setFormatOnSaveEnabled(enabled);
  }

  @Override
  public @Nullable ActionOnSaveComment getComment() {
    FormatOnSaveOptions options = ObjectUtils.notNull(getOptionsObjectStoringUiState(), FormatOnSaveOptions.getInstance(getProject()));

    if (options.isFormatOnSaveEnabled() && !options.isAllFileTypesSelected() && options.getSelectedFileTypes().isEmpty()) {
      return ActionOnSaveComment.warning(CodeInsightBundle.message("actions.on.save.page.warning.no.file.types.selected"));
    }

    return null;
  }

  private boolean isFormatOnlyChangedLines() {
    FormatOnSaveOptions options = ObjectUtils.notNull(getOptionsObjectStoringUiState(), FormatOnSaveOptions.getInstance(getProject()));
    return options.isFormatOnlyChangedLines();
  }

  private void setFormatOnlyChangedLines(boolean changedLines) {
    FormatOnSaveOptions options = getOrCreateOptionsObjectStoringUiState();
    options.setFormatOnlyChangedLines(changedLines);
  }


  private @Nullable FormatOnSaveOptions getOptionsObjectStoringUiState() {
    return getContext().getUserData(OPTIONS_FROM_UI_KEY);
  }

  private @NotNull FormatOnSaveOptions getOrCreateOptionsObjectStoringUiState() {
    FormatOnSaveOptions options = getOptionsObjectStoringUiState();
    if (options == null) {
      options = FormatOnSaveOptions.getInstance(getProject()).clone();
      getContext().putUserData(OPTIONS_FROM_UI_KEY, options);
    }
    return options;
  }

  @Override
  public @NotNull List<? extends ActionLink> getActionLinks() {
    return List.of(new ActionLink(CodeInsightBundle.message("actions.on.save.page.link.configure.scope"), new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        GeneralCodeStylePanel.selectFormatterTab(getSettings());
      }
    }));
  }

  @Override
  public @NotNull List<? extends DropDownLink<?>> getDropDownLinks() {
    DropDownLink<String> fileTypesLink = createFileTypesDropDownLink();
    DropDownLink<String> changedLinesLink = createChangedLinesDropDownLink();
    return changedLinesLink != null ? List.of(fileTypesLink, changedLinesLink) : List.of(fileTypesLink);
  }

  private @NotNull DropDownLink<String> createFileTypesDropDownLink() {
    Function1<DropDownLink<String>, JBPopup> popupBuilder = new Function1<>() {
      @Override
      public JBPopup invoke(DropDownLink<String> link) {
        return createFileTypesPopup(link);
      }
    };

    return new DropDownLink<>(getFileTypesLinkText(), popupBuilder);
  }

  private @NotNull @NlsContexts.LinkLabel String getFileTypesLinkText() {
    FormatOnSaveOptions options = ObjectUtils.notNull(getOptionsObjectStoringUiState(), FormatOnSaveOptions.getInstance(getProject()));

    if (options.isAllFileTypesSelected()) {
      return CodeInsightBundle.message("actions.on.save.page.label.all.file.types");
    }

    Set<String> fileTypes = options.getSelectedFileTypes();

    if (fileTypes.isEmpty()) {
      return CodeInsightBundle.message("actions.on.save.page.label.select.file.types");
    }

    String fileTypeName = fileTypes.iterator().next();
    FileType fileType = FileTypeRegistry.getInstance().findFileTypeByName(fileTypeName);
    String presentableFileType = fileType != null ? getFileTypePresentableName(fileType) : fileTypeName;

    if (fileTypes.size() == 1) {
      return CodeInsightBundle.message("actions.on.save.page.label.one.file.type.selected", presentableFileType);
    }

    return CodeInsightBundle.message("actions.on.save.page.label.many.file.types.selected", presentableFileType, fileTypes.size() - 1);
  }

  private @NotNull JBPopup createFileTypesPopup(@NotNull DropDownLink<String> link) {
    CheckedTreeNode root = new CheckedTreeNode(CodeInsightBundle.message("actions.on.save.page.label.all.file.types"));

    for (FileType fileType : getFormattableFileTypes()) {
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

  private static @NotNull SortedSet<FileType> getFormattableFileTypes() {
    SortedSet<FileType> result = new TreeSet<>(Comparator.comparing(FormatOnSaveActionInfo::getFileTypePresentableName));

    // add all file types that can be handled by the IDE internal formatter (== have FormattingModelBuilder)
    ExtensionPoint<KeyedLazyInstance<FormattingModelBuilder>> ep = LanguageFormatting.INSTANCE.getPoint();
    if (ep != null) {
      for (KeyedLazyInstance<FormattingModelBuilder> instance : ep.getExtensionList()) {
        String languageId = instance.getKey();
        Language language = Language.findLanguageByID(languageId);
        ContainerUtil.addIfNotNull(result, language != null ? language.getAssociatedFileType() : null);
      }
    }

    // Iterating only FormattingModelBuilders is not enough. Some FormattingModelBuilders may format several languages
    // (for example, JavascriptFormattingModelBuilder handles both JavaScript and ActionsScript). Also, some file types may get formatted by
    // external formatter integrated in the IDE (like ShExternalFormatter).
    //
    // A good sign that IDE supports some file type formatting is that it has a Code Style page for this file type. The following code makes
    // sure that all file types that have their Code Style pages are included in the result set.
    //
    // The logic of iterating Code Style pages is similar to what's done in CodeStyleSchemesConfigurable.buildConfigurables()
    for (CodeStyleSettingsProvider provider : CodeStyleSettingsProvider.EXTENSION_POINT_NAME.getExtensionList()) {
      Language language = provider.getLanguage();
      if (provider.hasSettingsPage() && language != null) {
        ContainerUtil.addIfNotNull(result, language.getAssociatedFileType());
      }
    }
    for (LanguageCodeStyleSettingsProvider provider : LanguageCodeStyleSettingsProvider.getSettingsPagesProviders()) {
      ContainerUtil.addIfNotNull(result, provider.getLanguage().getAssociatedFileType());
    }

    return result;
  }

  private @NotNull CheckboxTree createFileTypesCheckboxTree(@NotNull CheckedTreeNode root) {
    CheckboxTree tree = new CheckboxTree(createFileTypesRenderer(), root) {
      @Override
      protected void installSpeedSearch() {
        new TreeSpeedSearch(this, path -> {
          final CheckedTreeNode node = (CheckedTreeNode)path.getLastPathComponent();
          final Object userObject = node.getUserObject();
          if (userObject instanceof FileType) {
            return getFileTypePresentableName((FileType)userObject);
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
    FormatOnSaveOptions options = ObjectUtils.notNull(getOptionsObjectStoringUiState(), FormatOnSaveOptions.getInstance(getProject()));

    if (options.isAllFileTypesSelected()) {
      root.setChecked(true);
      return;
    }

    root.setChecked(false);

    Enumeration<TreeNode> fileTypesEnum = root.children();
    while (fileTypesEnum.hasMoreElements()) {
      CheckedTreeNode node = (CheckedTreeNode)fileTypesEnum.nextElement();
      FileType fileType = (FileType)node.getUserObject();
      node.setChecked(options.isFileTypeSelected(fileType));
    }
  }

  private void onFileTypePopupClosed(@NotNull DropDownLink<String> link, @NotNull CheckedTreeNode root) {
    FormatOnSaveOptions options = getOrCreateOptionsObjectStoringUiState();

    if (root.isChecked()) {
      options.setAllFileTypesSelected(true);
    }
    else {
      options.setAllFileTypesSelected(false);

      Enumeration<TreeNode> fileTypesEnum = root.children();
      while (fileTypesEnum.hasMoreElements()) {
        CheckedTreeNode node = (CheckedTreeNode)fileTypesEnum.nextElement();
        FileType fileType = (FileType)node.getUserObject();

        options.setFileTypeSelected(fileType, node.isChecked());
      }
    }

    link.setText(getFileTypesLinkText());
  }

  private static @NotNull CheckboxTree.CheckboxTreeCellRenderer createFileTypesRenderer() {
    return new CheckboxTree.CheckboxTreeCellRenderer() {
      @Override
      public void customizeRenderer(JTree t, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean focus) {
        if (!(value instanceof CheckedTreeNode)) return;

        final CheckedTreeNode node = (CheckedTreeNode)value;
        final Object userObject = node.getUserObject();

        if (userObject instanceof String) {
          getTextRenderer().append((String)userObject);
        }
        if (userObject instanceof FileType) {
          getTextRenderer().setIcon(((FileType)userObject).getIcon());
          getTextRenderer().append(getFileTypePresentableName(((FileType)userObject)));
        }
      }
    };
  }

  private static @NotNull @Nls String getFileTypePresentableName(@NotNull FileType fileType) {
    // in fact, the following is always true for file types handled here
    if (fileType instanceof LanguageFileType) {
      return ((LanguageFileType)fileType).getLanguage().getDisplayName();
    }
    return fileType.getDescription();
  }

  private @Nullable DropDownLink<String> createChangedLinesDropDownLink() {
    if (!VcsFacade.getInstance().hasActiveVcss(getProject())) return null;

    String wholeFile = CodeInsightBundle.message("actions.on.save.page.label.whole.file");
    String changedLines = CodeInsightBundle.message("actions.on.save.page.label.changed.lines");

    String current = isFormatOnlyChangedLines() ? changedLines : wholeFile;

    return new DropDownLink<>(current, List.of(wholeFile, changedLines), choice -> setFormatOnlyChangedLines(choice == changedLines));
  }

  @Override
  protected void apply() {
    if (getOptionsObjectStoringUiState() == null) {
      // nothing changed
      return;
    }

    FormatOnSaveOptions.getInstance(getProject()).loadState(getOrCreateOptionsObjectStoringUiState().getState().clone());
  }

  @Override
  protected boolean isModified() {
    if (getOptionsObjectStoringUiState() == null) {
      // nothing changed
      return false;
    }

    return !getOrCreateOptionsObjectStoringUiState().equals(FormatOnSaveOptions.getInstance(getProject()));
  }
}
