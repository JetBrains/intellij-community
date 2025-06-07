package com.intellij.database.dump;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.DataGridNotifications;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.extractors.DataExtractor;
import com.intellij.database.util.Out;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.TextTransferable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;

import static com.intellij.database.extractors.GridExtractorsUtilCore.prepareFileName;

public interface ExtractionHelper {
  @NotNull Out createOut(@Nullable String name, @NotNull DataExtractor extractor) throws IOException;

  boolean isSingleFileMode();

  void sourceDumped(@NotNull DataExtractor extractor, @NotNull Out out);

  void onFinished(@NotNull BiConsumer<Project, DumpInfo> consumer);

  void after(@NotNull Project project, @NotNull DumpInfo info) throws IOException;

  @NlsContexts.ProgressTitle
  @NotNull
  String getTitle(@NotNull String displayName);

  abstract class ExtractionHelperBase implements ExtractionHelper {
    private BiConsumer<Project, DumpInfo> myOnFinished = null;

    @Override
    public final void onFinished(@NotNull BiConsumer<Project, DumpInfo> consumer) {
      myOnFinished = consumer;
    }

    @Override
    public void after(@NotNull Project project, @NotNull DumpInfo info) throws IOException {
      if (myOnFinished != null) {
        myOnFinished.accept(project, info);
      }
    }
  }

  class FileExtractionHelper extends ExtractionHelperBase {
    private final File myFile;

    public FileExtractionHelper(@NotNull File file) {
      myFile = file;
    }

    @Override
    public @NotNull Out createOut(@Nullable String name, @NotNull DataExtractor extractor) throws IOException {
      File fileToWrite = createFileToWrite(name, extractor);
      FileOutputStream outputStream = new FileOutputStream(fileToWrite);
      return new Out.FromStream(new BufferedOutputStream(outputStream));
    }

    private File createFileToWrite(@Nullable String name, @NotNull DataExtractor extractor) {
      if (myFile.isFile()) return myFile;
      return findFile(name, extractor);
    }

    private File findFile(@Nullable String name, @NotNull DataExtractor extractor) {
      name = name != null ? name : "out";
      File result = createFile(name, extractor, null);
      int index = 1;
      while (result.exists()) {
        result = createFile(name, extractor, index++);
      }
      return result;
    }

    private File createFile(@NotNull String name, @NotNull DataExtractor extractor, @Nullable Integer index) {
      return new File(myFile, prepareFileName(name) + (index == null ? "" : "_" + index) + "." + extractor.getFileExtension());
    }

    @Override
    public boolean isSingleFileMode() {
      return myFile.isFile();
    }

    @Override
    public void sourceDumped(@NotNull DataExtractor extractor, @NotNull Out out) {
    }

    @Override
    public void after(@NotNull Project project, @NotNull DumpInfo info) throws IOException {
      super.after(project, info);
      notification(project, info);
      errorNotification(project, info);
      VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myFile);
      if (virtualFile != null) VfsUtil.markDirtyAndRefresh(false, false, false, virtualFile);
    }

    private void notification(@NotNull Project project, @NotNull DumpInfo info) {
      String title = info.getTitle();
      long rowCount = info.getRowCount();
      String producerName = info.getProducerName();
      String sourceName = info.getSourceName();
      int sourcesCount = info.getSourcesCount();
      boolean needSources = sourceName != null && sourcesCount > 1;
      String content = DataGridBundle.message("export.rows.saved.to.file.message", needSources ? 1 : 0, // 0
                                              sourcesCount, // 1
                                              sourceName, // 2
                                              rowCount, // 3
                                              producerName != null ? 1 : 0, // 4
                                              producerName, // 5
                                              myFile.isFile() ? 1 : 0 // 6
      );
      @NotNull NotificationType type = !info.getErrorSummary().isEmpty() ? NotificationType.ERROR : NotificationType.INFORMATION;
      @NotNull String path = myFile.getPath();
      DataGridNotifications.EXTRACTORS_GROUP
        .createNotification(title, GridUtil.getContent(content, path), type)
        .setDisplayId("FileExtractionHelper.finished")
        .setListener(new GridUtil.FileNotificationListener(project, path))
        .addAction(new GridUtil.GridRevealFileAction(path))
        .notify(project);
    }

    @Override
    public @NotNull String getTitle(@NotNull String displayName) {
      return DataGridBundle.message("export.save.to.file", displayName);
    }
  }

  static void errorNotification(@NotNull Project project, @NotNull DumpInfo info) {
    String summary = info.getErrorSummary();
    if (summary.isEmpty()) return;
    DataGridNotifications.EXTRACTORS_GROUP.createNotification(summary, NotificationType.ERROR)
      .setDisplayId("ExtractionHelper.error")
      .notify(project);
  }

  abstract class MemoryExtractionHelper extends ExtractionHelperBase {

    @Override
    public @NotNull Out createOut(@Nullable String name, @NotNull DataExtractor extractor) {
      return new Out.Readable();
    }

    @Override
    public boolean isSingleFileMode() {
      return false;
    }

    @Override
    public void sourceDumped(@NotNull DataExtractor extractor, @NotNull Out out) {
      if (!(out instanceof Out.Readable readableOut)) return;
      String data = readableOut.getString();
      dumpData(extractor, data);
    }

    protected abstract void dumpData(@NotNull DataExtractor extractor, @NotNull String data);

    @Override
    public void after(@NotNull Project project, @NotNull DumpInfo info) throws IOException {
      super.after(project, info);
      errorNotification(project, info);
    }
  }

  class ClipboardExtractionHelper extends MemoryExtractionHelper {
    @Override
    protected void dumpData(@NotNull DataExtractor extractor, @NotNull String data) {
      boolean htmlNeeded = extractor.getFileExtension().contains("htm");
      final Transferable content = htmlNeeded ? new TextTransferable(data) : new StringSelection(data);
      UIUtil.invokeLaterIfNeeded(() -> CopyPasteManager.getInstance().setContents(content));
    }

    @Override
    public @NotNull String getTitle(@NotNull String displayName) {
      return DataGridBundle.message("export.copy.to.clipboard", displayName);
    }
  }

  class TextExtractionHelper extends MemoryExtractionHelper {
    private final Ref<String> myRef = new Ref<>();

    @Override
    protected void dumpData(@NotNull DataExtractor extractor, @NotNull String data) {
      myRef.set(data);
    }

    public @Nullable String getDumpedResult() {
      return myRef.get();
    }

    @Override
    public @NotNull String getTitle(@NotNull String displayName) {
      return DataGridBundle.message("export.copy.to.text", displayName);
    }
  }
}
