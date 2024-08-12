// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vfs.encoding;

import com.intellij.ide.IdeBundle;
import com.intellij.lang.LangBundle;
import com.intellij.lang.PerFileMappingsEx;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.tree.PerFileConfigurableBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;

final class FileEncodingConfigurable extends PerFileConfigurableBase<Charset> {

  private final FileEncodingConfigurableUI ui = new FileEncodingConfigurableUI();
  private Charset myPropsCharset;

  private final Mapping<Charset> myProjectMapping;
  private final Mapping<Charset> myGlobalMapping;

  FileEncodingConfigurable(@NotNull Project project) {
    super(project, createMappings(project));
    EncodingManager app = EncodingManager.getInstance();
    EncodingProjectManagerImpl prj = (EncodingProjectManagerImpl)EncodingProjectManager.getInstance(myProject);
    myProjectMapping = new Mapping<>(IdeBundle.message("file.encoding.option.global.encoding"),
                                     () -> app.getDefaultCharsetName().isEmpty() ? null : app.getDefaultCharset(),
                                     o -> app.setDefaultCharsetName(getCharsetName(o)));
    myGlobalMapping = new Mapping<>(IdeBundle.message("file.encoding.option.project.encoding"),
                                    prj::getConfiguredDefaultCharset,
                                    o -> prj.setDefaultCharsetName(getCharsetName(o)));
  }

  @Override
  protected boolean isGlobalMapping(Mapping<Charset> prop) {
    return prop == myGlobalMapping || super.isGlobalMapping(prop);
  }

  @Override
  protected boolean isProjectMapping(Mapping<Charset> prop) {
    return prop == myProjectMapping || super.isProjectMapping(prop);
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("file.encodings.configurable");
  }

  @Override
  public String getHelpTopic() {
    return "reference.settingsdialog.project.file.encodings";
  }

  @Override
  public @NotNull String getId() {
    return "File.Encoding";
  }

  @Override
  protected <S> Object getParameter(@NotNull Key<S> key) {
    if (key == DESCRIPTION) return IdeBundle.message("encodings.dialog.caption", ApplicationNamesInfo.getInstance().getFullProductName());
    if (key == MAPPING_TITLE) return IdeBundle.message("file.encoding.option.encoding.column");
    if (key == TARGET_TITLE) return IdeBundle.message("file.encoding.option.path.column");
    if (key == OVERRIDE_QUESTION) return null;
    if (key == OVERRIDE_TITLE) return null;
    if (key == EMPTY_TEXT) return IdeBundle.message("file.encodings.not.configured");
    return null;
  }

  @Override
  protected void renderValue(@Nullable Object target, @NotNull Charset t, @NotNull ColoredTextContainer renderer) {
    VirtualFile file = target instanceof VirtualFile ? (VirtualFile)target : null;
    EncodingUtil.FailReason result = file == null || file.isDirectory() ? null : EncodingUtil.checkCanConvertAndReload(file);

    @NlsSafe String encodingText = t.displayName();
    SimpleTextAttributes attributes = result == null ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES;
    renderer.append(encodingText + (result == null ? "" : " (" + EncodingUtil.reasonToString(result, file) + ")"), attributes);
  }

  @Override
  protected @NotNull ActionGroup createActionListGroup(@Nullable Object target, @NotNull Consumer<? super Charset> onChosen) {
    VirtualFile file = target instanceof VirtualFile ? (VirtualFile)target : null;
    byte[] b = null;
    try {
      b = file == null || file.isDirectory() ? null : file.contentsToByteArray();
    }
    catch (IOException ignored) {
    }
    byte[] bytes = b;
    Document document = file == null ? null : FileDocumentManager.getInstance().getDocument(file);

    return new ChangeFileEncodingAction(true) {
      @Override
      protected boolean chosen(Document document, Editor editor, VirtualFile virtualFile, byte[] bytes, @NotNull Charset charset) {
        onChosen.consume(charset);
        return true;
      }
    }.createActionGroup(file, null, document, bytes, getClearValueText(target));
  }

  @Override
  protected @NlsActions.ActionText @Nullable String getClearValueText(@Nullable Object target) {
    return target != null ? super.getClearValueText(target) : LangBundle.message("action.set.system.default.encoding.text");
  }

  @Override
  protected @NlsActions.ActionText @Nullable String getNullValueText(@Nullable Object target) {
    return target != null ? super.getNullValueText(target) : IdeBundle.message("encoding.name.system.default", CharsetToolkit.getDefaultSystemCharset().displayName());
  }

  @Override
  protected @NotNull Collection<Charset> getValueVariants(@Nullable Object target) {
    return Arrays.asList(CharsetToolkit.getAvailableCharsets());
  }

  @Override
  public @NotNull JComponent createComponent() {
    final class PropertiesCharsetValue implements Value<Charset> {
      @Override
      public void commit() {}

      @Override
      public Charset get() {
        return myPropsCharset;
      }

      @Override
      public void set(Charset value) {
        myPropsCharset = adjustChosenValue(null, value);
      }
    }

    final String nullTextValue = IdeBundle.message("encoding.name.properties.default", StandardCharsets.ISO_8859_1.displayName());
    JComponent tablePanel = super.createComponent();
    Dimension size = tablePanel.getPreferredSize();
    tablePanel.setPreferredSize(new Dimension(400, size.height));
    return ui.createContent(tablePanel,
                            createActionPanel(new PerFileConfigurableComboBoxAction(new PropertiesCharsetValue(), null, nullTextValue)));
  }

  @Override
  protected @NotNull List<Mapping<Charset>> getDefaultMappings() {
    return Arrays.asList(
      myProjectMapping,
      myGlobalMapping);
  }

  @Override
  protected Charset adjustChosenValue(@Nullable Object target, Charset chosen) {
    return chosen == ChooseFileEncodingAction.NO_ENCODING ? null : chosen;
  }

  @Override
  public boolean isModified() {
    if (super.isModified()) return true;
    EncodingProjectManagerImpl encodingManager = (EncodingProjectManagerImpl)EncodingProjectManager.getInstance(myProject);
    boolean same = Comparing.equal(encodingManager.getDefaultCharsetForPropertiesFiles(null), myPropsCharset)
                   && encodingManager.isNative2AsciiForPropertiesFiles() == ui.transparentNativeToAsciiCheckBox.isSelected()
                   && encodingManager.getBOMForNewUTF8Files() == ui.bomForUTF8Combo.getSelectedItem();
    return !same;
  }

  private static @NotNull String getCharsetName(@Nullable Charset c) {
    return c == null ? "" : c.name();
  }

  @Override
  public void apply() throws ConfigurationException {
    super.apply();
    EncodingProjectManagerImpl encodingManager = (EncodingProjectManagerImpl)EncodingProjectManager.getInstance(myProject);
    encodingManager.setDefaultCharsetForPropertiesFiles(null, myPropsCharset);
    encodingManager.setNative2AsciiForPropertiesFiles(null, ui.transparentNativeToAsciiCheckBox.isSelected());
    EncodingProjectManagerImpl.BOMForNewUTF8Files option = ObjectUtils.notNull((EncodingProjectManagerImpl.BOMForNewUTF8Files)ui.bomForUTF8Combo.getSelectedItem(), EncodingProjectManagerImpl.BOMForNewUTF8Files.NEVER);
    encodingManager.setBOMForNewUtf8Files(option);
  }

  @Override
  public void reset() {
    EncodingProjectManagerImpl encodingManager = (EncodingProjectManagerImpl)EncodingProjectManager.getInstance(myProject);
    ui.transparentNativeToAsciiCheckBox.setSelected(encodingManager.isNative2AsciiForPropertiesFiles());
    myPropsCharset = encodingManager.getDefaultCharsetForPropertiesFiles(null);
    ui.bomForUTF8Combo.setSelectedItem(encodingManager.getBOMForNewUTF8Files());
    super.reset();
  }

  @Override
  protected boolean canEditTarget(@Nullable Object target, Charset value) {
    return target == null || target instanceof VirtualFile && (
      ((VirtualFile)target).isDirectory() || EncodingUtil.checkCanConvertAndReload((VirtualFile)target) == null);
  }

  private static @NotNull PerFileMappingsEx<Charset> createMappings(@NotNull Project project) {
    EncodingProjectManagerImpl prjManager = (EncodingProjectManagerImpl)EncodingProjectManager.getInstance(project);
    return new PerFileMappingsEx<>() {
      @Override
      public @NotNull Map<VirtualFile, Charset> getMappings() {
        return new HashMap<>(prjManager.getAllMappings());
      }

      @Override
      public void setMappings(@NotNull Map<VirtualFile, Charset> mappings) {
        prjManager.setMapping(mappings);
      }

      @Override
      public void setMapping(@Nullable VirtualFile file, Charset value) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Charset getMapping(@Nullable VirtualFile file) {
        throw new UnsupportedOperationException();
      }

      @Override
      public @Nullable Charset getDefaultMapping(@Nullable VirtualFile file) {
        return prjManager.getEncoding(file, true);
      }
    };
  }
}
