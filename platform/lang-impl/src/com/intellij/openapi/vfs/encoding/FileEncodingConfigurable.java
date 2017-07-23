/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.openapi.vfs.encoding;

import com.intellij.ide.IdeBundle;
import com.intellij.lang.PerFileMappings;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;
import com.intellij.util.Producer;
import com.intellij.util.ui.tree.PerFileConfigurableBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.vfs.encoding.ChooseFileEncodingAction.NO_ENCODING;

public class FileEncodingConfigurable extends PerFileConfigurableBase<Charset> {
  private JPanel myPanel;
  private JCheckBox myTransparentNativeToAsciiCheckBox;
  private JPanel myPropertiesFilesEncodingCombo;
  private JPanel myTablePanel;

  private Charset myPropsCharset;

  public FileEncodingConfigurable(@NotNull Project project) {
    super(project, createMappings(project));
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
    if (key == MAPPING_TITLE) return "Encoding";
    if (key == TARGET_TITLE) return "Path";
    if (key == OVERRIDE_QUESTION) return null;
    if (key == OVERRIDE_TITLE) return null;
    if (key == EMPTY_TEXT) return IdeBundle.message("file.encodings.not.configured");
    return null;
  }

  @Override
  protected void renderValue(@Nullable Object target, @NotNull Charset t, @NotNull ColoredTextContainer renderer) {
    VirtualFile file = target instanceof VirtualFile ? (VirtualFile)target : null;
    Pair<Charset, String> check = file == null || file.isDirectory() ? null : EncodingUtil.checkSomeActionEnabled(file);
    String failReason = check == null ? null : check.second;

    String encodingText = t.displayName();
    SimpleTextAttributes attributes = failReason == null ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES;
    renderer.append(encodingText + (failReason == null ? "" : " (" + failReason + ")"), attributes);
  }

  @NotNull
  @Override
  protected ActionGroup createActionListGroup(@Nullable Object target, @NotNull Consumer<Charset> onChosen) {
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
      protected boolean chosen(Document document,
                               Editor editor,
                               VirtualFile virtualFile,
                               byte[] bytes,
                               @NotNull Charset charset) {
        onChosen.consume(charset);
        return true;
      }
    }.createActionGroup(file, null, document, bytes, getClearValueText(target));
  }

  @Nullable
  @Override
  protected String getClearValueText(@Nullable Object target) {
    if (target == null) return "<System Default>";
    return super.getClearValueText(target);
  }

  @Nullable
  @Override
  protected String getNullValueText(@Nullable Object target) {
    if (target == null) return IdeBundle.message("encoding.name.system.default", CharsetToolkit.getDefaultSystemCharset().displayName());
    return super.getNullValueText(target);
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
    JPanel p = createActionPanel(null, new Value<Charset>() {
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

  @NotNull
  @Override
  protected List<Trinity<String, Producer<Charset>, Consumer<Charset>>> getDefaultMappings() {
    EncodingManager app = EncodingManager.getInstance();
    EncodingProjectManagerImpl prj = (EncodingProjectManagerImpl)EncodingProjectManager.getInstance(myProject);
    return Arrays.asList(
      Trinity.create("Global Encoding",
                     () -> app.getDefaultCharsetName().isEmpty() ? null : app.getDefaultCharset(),
                     o -> app.setDefaultCharsetName(getCharsetName(o))),
      Trinity.create("Project Encoding",
                     () -> prj.getConfiguredDefaultCharset(),
                     o -> prj.setDefaultCharsetName(getCharsetName(o))));
  }

  @Override
  protected Charset adjustChosenValue(@Nullable Object target, Charset chosen) {
    return chosen == NO_ENCODING ? null : chosen;
  }

  @Override
  public boolean isModified() {
    if (super.isModified()) return true;
    EncodingProjectManagerImpl prjManager = (EncodingProjectManagerImpl)EncodingProjectManager.getInstance(myProject);
    boolean same = Comparing.equal(prjManager.getDefaultCharsetForPropertiesFiles(null), myPropsCharset)
                   && prjManager.isNative2AsciiForPropertiesFiles() == myTransparentNativeToAsciiCheckBox.isSelected();
    return !same;
  }

  @NotNull
  private static String getCharsetName(@Nullable Charset c) {
    return c == null ? "" : c.name();
  }

  @Override
  public void apply() throws ConfigurationException {
    super.apply();
    EncodingProjectManagerImpl prjManager = ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(myProject));
    prjManager.setDefaultCharsetForPropertiesFiles(null, myPropsCharset);
    prjManager.setNative2AsciiForPropertiesFiles(null, myTransparentNativeToAsciiCheckBox.isSelected());
  }

  @Override
  public void reset() {
    EncodingProjectManager prjManager = EncodingProjectManager.getInstance(myProject);
    myTransparentNativeToAsciiCheckBox.setSelected(prjManager.isNative2AsciiForPropertiesFiles());
    myPropsCharset = prjManager.getDefaultCharsetForPropertiesFiles(null);
    super.reset();
  }

  @Override
  protected boolean canEditTarget(@Nullable Object target, Charset value) {
    return target == null || target instanceof VirtualFile && (
      ((VirtualFile)target).isDirectory() || EncodingUtil.checkSomeActionEnabled(((VirtualFile)target)) == null);
  }

  @NotNull
  private static PerFileMappings<Charset> createMappings(@NotNull Project project) {
    EncodingProjectManagerImpl prjManager = (EncodingProjectManagerImpl)EncodingProjectManager.getInstance(project);
    return new PerFileMappings<Charset>() {
      @NotNull
      @Override
      public Map<VirtualFile, Charset> getMappings() {
        return prjManager.getAllMappings();
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
