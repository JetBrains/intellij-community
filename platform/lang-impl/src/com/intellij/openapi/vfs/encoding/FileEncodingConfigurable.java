// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.tree.PerFileConfigurableBase;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

class FileEncodingConfigurable extends PerFileConfigurableBase<Charset> {
  private JPanel myPanel;
  private JCheckBox myTransparentNativeToAsciiCheckBox;
  private JPanel myPropertiesFilesEncodingCombo;
  private JPanel myTablePanel;
  private ComboBox<EncodingProjectManagerImpl.BOMForNewUTF8Files> myBOMForUTF8Combo;
  private HyperlinkLabel myExplanationLabel;

  private Charset myPropsCharset;
  private final Trinity<String, Supplier<? extends Charset>, Consumer<? super Charset>> myProjectMapping;
  private final Trinity<String, Supplier<? extends Charset>, Consumer<? super Charset>> myGlobalMapping;

  FileEncodingConfigurable(@NotNull Project project) {
    super(project, createMappings(project));
    myBOMForUTF8Combo.setModel(new EnumComboBoxModel<>(EncodingProjectManagerImpl.BOMForNewUTF8Files.class));
    myBOMForUTF8Combo.addItemListener(e -> updateExplanationLabelText());
    myExplanationLabel.setHyperlinkTarget("https://en.wikipedia.org/wiki/Byte_order_mark#UTF-8");
    EncodingManager app = EncodingManager.getInstance();
    EncodingProjectManagerImpl prj = (EncodingProjectManagerImpl)EncodingProjectManager.getInstance(myProject);
    myProjectMapping = Trinity.create(IdeBundle.message("file.encoding.option.global.encoding"),
                                      () -> app.getDefaultCharsetName().isEmpty() ? null : app.getDefaultCharset(),
                                      o -> app.setDefaultCharsetName(getCharsetName(o)));
    myGlobalMapping = Trinity.create(IdeBundle.message("file.encoding.option.project.encoding"),
                                     prj::getConfiguredDefaultCharset,
                                     o -> prj.setDefaultCharsetName(getCharsetName(o)));
  }

  @Override
  protected boolean isGlobalMapping(Trinity<@NlsContexts.Label String, Supplier<? extends Charset>, Consumer<? super Charset>> prop) {
    return prop == myGlobalMapping || super.isGlobalMapping(prop);
  }

  @Override
  protected boolean isProjectMapping(Trinity<@NlsContexts.Label String, Supplier<? extends Charset>, Consumer<? super Charset>> prop) {
    return prop == myProjectMapping || super.isProjectMapping(prop);
  }

  private void updateExplanationLabelText() {
    EncodingProjectManagerImpl.BOMForNewUTF8Files item = (EncodingProjectManagerImpl.BOMForNewUTF8Files)myBOMForUTF8Combo.getSelectedItem();
    String I = ApplicationNamesInfo.getInstance().getProductName();
    if (item != null) {
      switch (item) {
        case ALWAYS:
          myExplanationLabel.setHtmlText(IdeBundle.message("file.encoding.option.warning.always", I));
          break;
        case NEVER:
          myExplanationLabel.setHtmlText(IdeBundle.message("file.encoding.option.warning.never", I));
          break;
        case WINDOWS_ONLY:
          myExplanationLabel.setHtmlText(IdeBundle.message("file.encoding.option.warning.windows.only", I));
          break;
      }
    }
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("file.encodings.configurable");
  }

  @Override
  @Nullable
  public String getHelpTopic() {
    return "reference.settingsdialog.project.file.encodings";
  }

  @Override
  @NotNull
  public String getId() {
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

  @NotNull
  @Override
  protected ActionGroup createActionListGroup(@Nullable Object target, @NotNull Consumer<? super Charset> onChosen) {
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

  @NotNull
  @Override
  protected Collection<Charset> getValueVariants(@Nullable Object target) {
    return Arrays.asList(CharsetToolkit.getAvailableCharsets());
  }

  @NotNull
  @Override
  public JComponent createComponent() {
    myTablePanel.add(super.createComponent(), BorderLayout.CENTER);
    JPanel p = createActionPanel(null, new Value<>() {
      @Override
      public void commit() {}

      @Override
      public Charset get() {
        return myPropsCharset;
      }

      @Override
      public void set(Charset value) {
        myPropsCharset = value;
      }
    });
    myPropertiesFilesEncodingCombo.add(p, BorderLayout.CENTER);
    return myPanel;
  }

  @Override
  protected @NotNull List<Trinity<@Nls String, Supplier<? extends Charset>, Consumer<? super Charset>>> getDefaultMappings() {
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
                   && encodingManager.isNative2AsciiForPropertiesFiles() == myTransparentNativeToAsciiCheckBox.isSelected()
                   && encodingManager.getBOMForNewUTF8Files() == myBOMForUTF8Combo.getSelectedItem()
      ;
    return !same;
  }

  @NotNull
  private static String getCharsetName(@Nullable Charset c) {
    return c == null ? "" : c.name();
  }

  @Override
  public void apply() throws ConfigurationException {
    super.apply();
    EncodingProjectManagerImpl encodingManager = (EncodingProjectManagerImpl)EncodingProjectManager.getInstance(myProject);
    encodingManager.setDefaultCharsetForPropertiesFiles(null, myPropsCharset);
    encodingManager.setNative2AsciiForPropertiesFiles(null, myTransparentNativeToAsciiCheckBox.isSelected());
    EncodingProjectManagerImpl.BOMForNewUTF8Files option = ObjectUtils.notNull((EncodingProjectManagerImpl.BOMForNewUTF8Files)myBOMForUTF8Combo.getSelectedItem(), EncodingProjectManagerImpl.BOMForNewUTF8Files.NEVER);
    encodingManager.setBOMForNewUtf8Files(option);
  }

  @Override
  public void reset() {
    EncodingProjectManagerImpl encodingManager = (EncodingProjectManagerImpl)EncodingProjectManager.getInstance(myProject);
    myTransparentNativeToAsciiCheckBox.setSelected(encodingManager.isNative2AsciiForPropertiesFiles());
    myPropsCharset = encodingManager.getDefaultCharsetForPropertiesFiles(null);
    myBOMForUTF8Combo.setSelectedItem(encodingManager.getBOMForNewUTF8Files());
    super.reset();
  }

  @Override
  protected boolean canEditTarget(@Nullable Object target, Charset value) {
    return target == null || target instanceof VirtualFile && (
      ((VirtualFile)target).isDirectory() || EncodingUtil.checkCanConvertAndReload((VirtualFile)target) == null);
  }

  @NotNull
  private static PerFileMappingsEx<Charset> createMappings(@NotNull Project project) {
    EncodingProjectManagerImpl prjManager = (EncodingProjectManagerImpl)EncodingProjectManager.getInstance(project);
    return new PerFileMappingsEx<>() {
      @NotNull
      @Override
      public Map<VirtualFile, Charset> getMappings() {
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

      @Nullable
      @Override
      public Charset getDefaultMapping(@Nullable VirtualFile file) {
        return prjManager.getEncoding(file, true);
      }
    };
  }
}
