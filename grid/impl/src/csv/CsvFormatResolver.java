package com.intellij.database.csv;

import com.intellij.database.editor.CsvTableFileEditor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.AttributeInputStream;
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

public final class CsvFormatResolver extends CsvFormatResolverCore {
  private static final Logger LOG = Logger.getInstance(CsvFormatResolver.class);

  private static final FileAttribute CSV_FORMAT_ATTRIBUTES = new FileAttribute("csv_format", 2, true);
  private CsvFormatResolver() {
  }

  public static @Nullable CsvFormat getFormatFromState(@NotNull Project project, @NotNull VirtualFile file) {
    FileEditorProvider provider = FileEditorProviderManager.getInstance().getProvider("csv-data-editor");
    if (provider == null) return null;
    if (project.isDisposed()) return null;
    CsvTableFileEditor csvEditor = ContainerUtil.findInstance(FileEditorManager.getInstance(project).getAllEditors(file), CsvTableFileEditor.class);
    if (csvEditor != null) return csvEditor.getFormat();
    EditorHistoryManager editorHistoryManager = EditorHistoryManager.getInstance(project);
    FileEditorState state = editorHistoryManager.getState(file, provider);
    return state != null ? readCsvFormat(state) : null;
  }

  public static @Nullable CsvFormat getFormatFromFile(@NotNull VirtualFile file) {
    //todo
    AttributeInputStream stream = file instanceof VirtualFileWithId ? CSV_FORMAT_ATTRIBUTES.readFileAttribute(file) : null;
    if (stream == null) return null;
    try (AttributeInputStream resource = stream) {
      String id = resource.readEnumeratedString();
      Element load = JDOMUtil.load(id);
      PersistentCsvFormat format = XmlSerializer.deserialize(load, PersistentCsvFormat.class);
      return format.immutable();
    }
    catch (IOException | JDOMException e) {
      LOG.warn(e);
    }
    return null;
  }

  public static void saveCsvFormat(@NotNull CsvFormat format, @NotNull VirtualFile file) {
    PersistentCsvFormat persistentFormat = new PersistentCsvFormat(format);
    Element element = XmlSerializer.serialize(persistentFormat);
    String string = JDOMUtil.write(element, "");
    try (AttributeOutputStream resource = CSV_FORMAT_ATTRIBUTES.writeFileAttribute(file)) {
      resource.writeEnumeratedString(string);
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  public static void saveCsvFormatAndUpdateEditor(@Nullable Project project, @NotNull CsvFormat format, @NotNull VirtualFile file) {
    saveCsvFormat(format, file);
    if (project != null) {
      CsvTableFileEditor csvEditor = ContainerUtil.findInstance(FileEditorManager.getInstance(project).getEditors(file), CsvTableFileEditor.class);
      if (csvEditor != null) {
        csvEditor.setFormat(format);
      }
    }
  }

  public static @Nullable CsvFormat readCsvFormat(@Nullable FileEditorState state) {
    State csvState = ObjectUtils.tryCast(state, State.class);
    return csvState != null ? csvState.format.immutable() : null;
  }

  public static @Nullable CsvFormat getFormat(@Nullable Project project, @NotNull VirtualFile file, boolean tryDetectHeader,
                                              @Nullable Supplier<List<CsvFormat>> existingFormatsSupplier) {
    return getFormat(project, file, tryDetectHeader, existingFormatsSupplier, FormatGetter.FILE, FormatGetter.STATE, FormatGetter.CONTENT);
  }

  public static @Nullable CsvFormat getFormat(@Nullable Project project, @NotNull VirtualFile file, boolean tryDetectHeader,
                                              @Nullable Supplier<List<CsvFormat>> existingFormatsSupplier,
                                              FormatGetter @NotNull ... getters) {
    for (FormatGetter getter : getters) {
      CsvFormat format = getter.get(project, file, tryDetectHeader, existingFormatsSupplier);
      if (format != null) return format;
    }
    return null;
  }
  public static class State implements FileEditorState {
    @Tag("format")
    public PersistentCsvFormat format;

    public State() {
    }

    public State(@NotNull CsvFormat format) {
      this.format = new PersistentCsvFormat(format);
    }

    @Override
    public boolean canBeMergedWith(@NotNull FileEditorState otherState, @NotNull FileEditorStateLevel level) {
      return false;
    }
  }

  public enum FormatGetter {
    STATE {
      @Nullable
      @Override
      CsvFormat get(@Nullable Project project, @NotNull VirtualFile file, boolean tryDetectHeader,
                    @Nullable Supplier<List<CsvFormat>> existingFormatsSupplier) {
        return project == null ? null : getFormatFromState(project, file);
      }
    },
    FILE {
      @Nullable
      @Override
      CsvFormat get(@Nullable Project project, @NotNull VirtualFile file, boolean tryDetectHeader,
                    @Nullable Supplier<List<CsvFormat>> existingFormatsSupplier) {
        return getFormatFromFile(file);
      }
    },
    CONTENT {
      @Nullable
      @Override
      CsvFormat get(@Nullable Project project, @NotNull VirtualFile file, boolean tryDetectHeader,
                    @Nullable Supplier<List<CsvFormat>> existingFormatsSupplier) {
        return getMoreSuitableCsvFormat(file, tryDetectHeader, existingFormatsSupplier);
      }
    };

    abstract @Nullable CsvFormat get(@Nullable Project project, @NotNull VirtualFile file, boolean tryDetectHeader,
                                     @Nullable Supplier<List<CsvFormat>> existingFormatsSupplier);
  }
}
