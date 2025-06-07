package com.intellij.database.editor;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.database.csv.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.serialization.SerializationException;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author gregsh
 */
public final class CsvTableFileEditorProvider extends WeighedFileEditorProvider {
  public static final String PROVIDER_ID = "csv-data-editor";

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return isCsvFile(file);
  }

  @Override
  public boolean acceptRequiresReadAction() {
    return false;
  }

  @Override
  public @NotNull CsvTableFileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    CsvFormat format = getFormatFast(project, file);
    if (format != null) {
      return new CsvTableFileEditor(project, file, format);
    }
    CsvTableFileEditor editor = new CsvTableFileEditor(project, file, CsvFormats.CSV_FORMAT.get());
    ModalityState state = ModalityState.current();
    BackgroundTaskUtil.executeOnPooledThread(editor, () -> {
      CsvFormat detected = CsvFormatResolverCore.getMoreSuitableCsvFormat(file, true, null);
      ApplicationManager.getApplication().invokeLater(() -> {
        if (detected != null) {
          editor.setFormat(detected);
        }
      }, state);
    });
    return editor;
  }

  @Override
  public @NotNull FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
    FileEditorState state = null;
    try {
      state = XmlSerializer.deserialize(sourceElement, CsvFormatResolver.State.class);
    }
    catch (SerializationException ignore) {
    }
    return state == null ? FileEditorState.INSTANCE : state;
  }

  @Override
  public void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {
    if (state instanceof CsvFormatResolver.State) {
      XmlSerializer.serializeObjectInto(state, targetElement);
    }
  }

  @Override
  public @NotNull String getEditorTypeId() {
    return PROVIDER_ID;
  }

  @Override
  public @NotNull FileEditorPolicy getPolicy() {
    if (PlatformUtils.isPyCharm() || PlatformUtils.isDataSpell()) {
      return FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR;
    }
    else {
      return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR;
    }
  }

  private static @Nullable CsvFormat getFormatFast(@NotNull Project project, @NotNull VirtualFile file) {
    return CsvFormatResolver.getFormat(project, file, true, null, CsvFormatResolver.FormatGetter.FILE, CsvFormatResolver.FormatGetter.STATE);
  }

  private static boolean isCsvFile(@NotNull VirtualFile file) {
    return FileTypeRegistry.getInstance().isFileOfType(file, CsvFileType.INSTANCE);
  }

  public static void openEditor(@NotNull Project project, @NotNull VirtualFile file, @NotNull CsvFormat format) {
    FileEditorManagerEx fileEditorManagerEx = FileEditorManagerEx.getInstanceEx(project);
    EditorWindow editorWindow = fileEditorManagerEx.getSplitters().getCurrentWindow();
    EditorComposite composite = editorWindow == null ? null : editorWindow.getComposite(file);
    if (composite != null) {
      CsvTableFileEditor tableEditor = ContainerUtil.findInstance(composite.getAllEditors(), CsvTableFileEditor.class);
      if (tableEditor != null) {
        tableEditor.setFormat(format);
      }
      else {
        FileEditorProvider provider = getProvider();
        composite.addEditor(new CsvTableFileEditor(project, file, format), Objects.requireNonNull(provider));
      }
      fileEditorManagerEx.setSelectedEditor(file, PROVIDER_ID);
    }
  }

  public static @Nullable CsvTableFileEditorProvider getProvider() {
    return (CsvTableFileEditorProvider)FileEditorProviderManager.getInstance().getProvider(PROVIDER_ID);
  }
}
