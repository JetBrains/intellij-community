// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.external;

import com.intellij.CommonBundle;
import com.intellij.diff.contents.*;
import com.intellij.diff.merge.MergeResult;
import com.intellij.diff.merge.ThreesideMergeRequest;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.diff.util.ThreeSide;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.LineSeparator;
import com.intellij.util.PathUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public final class ExternalDiffToolUtil {
  public static boolean canCreateFile(@NotNull DiffContent content) {
    if (content instanceof EmptyContent) return true;
    if (content instanceof DocumentContent) return true;
    if (content instanceof FileContent) {
      VirtualFile file = ((FileContent)content).getFile();
      if (DiffUtil.isFileWithoutContent(file)) return false;
      return true;
    }
    if (content instanceof DirectoryContent) return ((DirectoryContent)content).getFile().isInLocalFileSystem();
    return false;
  }

  @NotNull
  private static InputFile createFile(@Nullable Project project, @NotNull DiffContent content, @NotNull FileNameInfo fileName)
    throws IOException {

    if (content instanceof EmptyContent) {
      return new TempInputFile(createFile(new byte[0], fileName));
    }
    else if (content instanceof FileContent) {
      VirtualFile file = ((FileContent)content).getFile();

      Document document = FileDocumentManager.getInstance().getCachedDocument(file);
      if (document != null) {
        FileDocumentManager.getInstance().saveDocument(document);
      }

      if (file.isInLocalFileSystem()) {
        return new LocalInputFile(file);
      }

      return new TempInputFile(createTempFile(file, fileName));
    }
    else if (content instanceof DocumentContent) {
      return new TempInputFile(createTempFile(project, (DocumentContent)content, fileName));
    }
    else if (content instanceof DirectoryContent) {
      VirtualFile file = ((DirectoryContent)content).getFile();
      if (file.isInLocalFileSystem()) {
        return new LocalInputFile(file);
      }

      throw new IllegalArgumentException(content.toString());
    }

    throw new IllegalArgumentException(content.toString());
  }

  @NotNull
  private static File createTempFile(@Nullable Project project,
                                     @NotNull DocumentContent content,
                                     @NotNull FileNameInfo fileName) throws IOException {
    FileDocumentManager.getInstance().saveDocument(content.getDocument());

    LineSeparator separator = content.getLineSeparator();
    if (separator == null) separator = LineSeparator.getSystemLineSeparator();

    Charset charset = getContentCharset(project, content);

    Boolean hasBom = content.hasBom();
    if (hasBom == null) hasBom = CharsetToolkit.getMandatoryBom(charset) != null;

    String contentData = content.getDocument().getText();
    if (separator != LineSeparator.LF) {
      contentData = StringUtil.convertLineSeparators(contentData, separator.getSeparatorString());
    }

    byte[] bytes = contentData.getBytes(charset);

    byte[] bom = hasBom ? CharsetToolkit.getPossibleBom(charset) : null;
    if (bom != null) {
      bytes = ArrayUtil.mergeArrays(bom, bytes);
    }

    return createFile(bytes, fileName);
  }

  @NotNull
  private static File createTempFile(@NotNull VirtualFile file, @NotNull FileNameInfo fileName) throws IOException {
    byte[] bytes = file.contentsToByteArray();
    return createFile(bytes, fileName);
  }

  @NotNull
  private static Charset getContentCharset(@Nullable Project project, @NotNull DocumentContent content) {
    Charset charset = content.getCharset();
    if (charset != null) return charset;
    EncodingManager e = project != null ? EncodingProjectManager.getInstance(project) : EncodingManager.getInstance();
    return e.getDefaultCharset();
  }

  @NotNull
  private static OutputFile createOutputFile(@Nullable Project project,
                                             @NotNull DiffContent content,
                                             @NotNull FileNameInfo fileName) throws IOException {
    if (content instanceof FileContent) {
      VirtualFile file = ((FileContent)content).getFile();

      Document document = FileDocumentManager.getInstance().getCachedDocument(file);
      if (document != null) {
        FileDocumentManager.getInstance().saveDocument(document);
      }

      if (file.isInLocalFileSystem()) {
        return new LocalOutputFile(file);
      }

      File tempFile = createTempFile(file, fileName);
      return new NonLocalOutputFile(file, tempFile);
    }
    else if (content instanceof DocumentContent) {
      DocumentContent documentContent = (DocumentContent)content;
      File tempFile = createTempFile(project, documentContent, fileName);
      Charset charset = getContentCharset(project, documentContent);
      return new DocumentOutputFile(documentContent.getDocument(), charset, tempFile);
    }
    throw new IllegalArgumentException(content.toString());
  }

  @NotNull
  private static File createFile(byte @NotNull [] bytes, @NotNull FileNameInfo fileName) throws IOException {
    File tempFile = FileUtil.createTempFile(fileName.prefix + "_", "_" + fileName.name, true);
    FileUtil.writeToFile(tempFile, bytes);
    return tempFile;
  }

  public static void execute(@Nullable Project project,
                             @NotNull ExternalDiffSettings.ExternalTool externalTool,
                             @NotNull List<? extends DiffContent> contents,
                             @NotNull List<String> titles,
                             @Nullable String windowTitle)
    throws IOException, ExecutionException {
    assert contents.size() == 2 || contents.size() == 3;
    assert titles.size() == contents.size();

    List<InputFile> files = new ArrayList<>();
    for (int i = 0; i < contents.size(); i++) {
      DiffContent content = contents.get(i);
      FileNameInfo fileName = FileNameInfo.create(contents, titles, windowTitle, i);
      files.add(createFile(project, content, fileName));
    }

    Map<String, String> patterns = new HashMap<>();
    if (files.size() == 2) {
      patterns.put("%1", files.get(0).getPath());
      patterns.put("%2", files.get(1).getPath());
      patterns.put("%3", "");
    }
    else {
      patterns.put("%1", files.get(0).getPath());
      patterns.put("%2", files.get(2).getPath());
      patterns.put("%3", files.get(1).getPath());
    }

    execute(externalTool.getProgramPath(), externalTool.getArgumentPattern(), patterns);
  }

  @RequiresEdt
  public static void executeMerge(@Nullable Project project,
                                  @NotNull ExternalDiffSettings.ExternalTool externalTool,
                                  @NotNull ThreesideMergeRequest request,
                                  @Nullable JComponent parentComponent) throws IOException, ExecutionException {
    request.onAssigned(true);
    try {
      boolean success = false;
      try {
        success = tryExecuteMerge(project, externalTool, request, parentComponent);
      }
      finally {
        request.applyResult(success ? MergeResult.RESOLVED : MergeResult.CANCEL);
      }
    }
    finally {
      request.onAssigned(false);
    }
  }

  public static boolean tryExecuteMerge(@Nullable Project project,
                                        @NotNull ExternalDiffSettings.ExternalTool externalTool,
                                        @NotNull ThreesideMergeRequest request,
                                        @Nullable JComponent parentComponent) throws IOException, ExecutionException {
    boolean success;
    OutputFile outputFile = null;
    List<InputFile> inputFiles = new ArrayList<>();
    try {
      DiffContent outputContent = request.getOutputContent();
      List<? extends DiffContent> contents = request.getContents();
      List<String> titles = request.getContentTitles();
      String windowTitle = request.getTitle();

      assert contents.size() == 3;
      assert titles.size() == contents.size();

      for (int i = 0; i < contents.size(); i++) {
        DiffContent content = contents.get(i);
        FileNameInfo fileName = FileNameInfo.create(contents, titles, windowTitle, i);
        inputFiles.add(createFile(project, content, fileName));
      }

      outputFile = createOutputFile(project, outputContent, FileNameInfo.createMergeResult(outputContent, windowTitle));

      Map<String, String> patterns = new HashMap<>();
      patterns.put("%1", inputFiles.get(0).getPath());
      patterns.put("%2", inputFiles.get(2).getPath());
      patterns.put("%3", inputFiles.get(1).getPath());
      patterns.put("%4", outputFile.getPath());

      final Process process = execute(externalTool.getProgramPath(), externalTool.getArgumentPattern(), patterns);

      if (externalTool.isMergeTrustExitCode()) {
        final Ref<Boolean> resultRef = new Ref<>();

        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
          final Semaphore semaphore = new Semaphore(0);

          final Thread waiter = new Thread("external process waiter") {
            @Override
            public void run() {
              try {
                resultRef.set(process.waitFor() == 0);
              }
              catch (InterruptedException ignore) {
              }
              finally {
                semaphore.release();
              }
            }
          };
          waiter.start();

          try {
            while (true) {
              ProgressManager.checkCanceled();
              if (semaphore.tryAcquire(200, TimeUnit.MILLISECONDS)) break;
            }
          }
          catch (InterruptedException ignore) {
          }
          finally {
            waiter.interrupt();
          }
        }, DiffBundle.message("waiting.for.external.tool"), true, project, parentComponent);
        success = resultRef.get() == Boolean.TRUE;
      }
      else {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
          TimeoutUtil.sleep(1000);
        }, DiffBundle.message("launching.external.tool"), false, project, parentComponent);

        success = Messages.showYesNoDialog(project,
                                           DiffBundle.message("press.mark.as.resolve"),
                                           DiffBundle.message("merge.in.external.tool"),
                                           DiffBundle.message("mark.as.resolved"),
                                           CommonBundle.message("button.revert"), null) == Messages.YES;
      }

      if (success) outputFile.apply();
    }
    finally {

      if (outputFile != null) outputFile.cleanup();
      for (InputFile file : inputFiles) {
        file.cleanup();
      }
    }
    return success;
  }

  @NotNull
  private static Process execute(@NotNull String exePath, @NotNull String parametersTemplate, @NotNull Map<String, String> patterns)
    throws ExecutionException {
    List<String> parameters = ParametersListUtil.parse(parametersTemplate, true);

    List<String> from = new ArrayList<>();
    List<String> to = new ArrayList<>();
    for (Map.Entry<String, String> entry : patterns.entrySet()) {
      from.add(entry.getKey());
      to.add(entry.getValue());
    }

    List<String> args = new ArrayList<>();
    for (String parameter : parameters) {
      String arg = StringUtil.replace(parameter, from, to);
      if (!StringUtil.isEmptyOrSpaces(arg)) args.add(arg);
    }

    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(exePath);
    commandLine.addParameters(args);
    return commandLine.createProcess();
  }

  //
  // Helpers
  //


  private interface InputFile {
    @NotNull
    String getPath();

    void cleanup();
  }

  private interface OutputFile extends InputFile {
    void apply() throws IOException;
  }

  private static class LocalOutputFile extends LocalInputFile implements OutputFile {
    LocalOutputFile(@NotNull VirtualFile file) {
      super(file);
    }

    @Override
    public void apply() {
      myFile.refresh(false, false);
    }
  }

  private static class NonLocalOutputFile extends TempInputFile implements OutputFile {
    @NotNull private final VirtualFile myFile;

    NonLocalOutputFile(@NotNull VirtualFile file, @NotNull File localFile) {
      super(localFile);
      myFile = file;
    }

    @Override
    public void apply() throws IOException {
      FileUtil.copy(myLocalFile, VfsUtilCore.virtualToIoFile(myFile));
      VfsUtil.markDirty(false, false, myFile);
    }
  }

  private static class DocumentOutputFile extends TempInputFile implements OutputFile {
    @NotNull private final Document myDocument;
    @NotNull private final Charset myCharset;

    DocumentOutputFile(@NotNull Document document, @NotNull Charset charset, @NotNull File localFile) {
      super(localFile);
      myDocument = document;
      myCharset = charset;
    }

    @Override
    public void apply() throws IOException {
      final String content = StringUtil.convertLineSeparators(FileUtil.loadFile(myLocalFile, myCharset));
      ApplicationManager.getApplication().runWriteAction(() -> myDocument.setText(content));
    }
  }

  private static class LocalInputFile implements InputFile {
    @NotNull protected final VirtualFile myFile;

    LocalInputFile(@NotNull VirtualFile file) {
      myFile = file;
    }

    @NotNull
    @Override
    public String getPath() {
      return myFile.getPath();
    }

    @Override
    public void cleanup() {
    }
  }

  private static class TempInputFile implements InputFile {
    @NotNull protected final File myLocalFile;

    TempInputFile(@NotNull File localFile) {
      myLocalFile = localFile;
    }

    @NotNull
    @Override
    public String getPath() {
      return myLocalFile.getPath();
    }

    @Override
    public void cleanup() {
      FileUtil.delete(myLocalFile);
    }
  }

  private static class FileNameInfo {
    @NotNull public final String prefix;
    @NotNull public final String name;

    FileNameInfo(@NotNull @NonNls String prefix, @NotNull @NonNls String name) {
      this.prefix = prefix;
      this.name = name;
    }

    @NotNull
    public static FileNameInfo create(@NotNull List<? extends DiffContent> contents,
                                      @NotNull List<String> titles,
                                      @Nullable String windowTitle,
                                      int index) {
      if (contents.size() == 2) {
        Side side = Side.fromIndex(index);
        DiffContent content = side.select(contents);
        String title = side.select(titles);
        String prefix = side.select("before", "after");

        String name = getFileName(content, title, windowTitle);
        return new FileNameInfo(prefix, name);
      }
      else if (contents.size() == 3) {
        ThreeSide side = ThreeSide.fromIndex(index);
        DiffContent content = side.select(contents);
        String title = side.select(titles);
        String prefix = side.select("left", "base", "right");

        String name = getFileName(content, title, windowTitle);
        return new FileNameInfo(prefix, name);
      }
      else {
        throw new IllegalArgumentException(String.valueOf(contents.size()));
      }
    }

    @NotNull
    public static FileNameInfo createMergeResult(@NotNull DiffContent content, @Nullable String windowTitle) {
      String name = getFileName(content, null, windowTitle);
      return new FileNameInfo("merge_result", name);
    }

    @NotNull
    private static String getFileName(@NotNull DiffContent content,
                                      @Nullable String title,
                                      @Nullable String windowTitle) {
      if (content instanceof EmptyContent) {
        return "no_content.tmp";
      }

      String fileName = content.getUserData(DiffUserDataKeysEx.FILE_NAME);

      if (fileName == null && content instanceof DocumentContent) {
        VirtualFile highlightFile = ((DocumentContent)content).getHighlightFile();
        fileName = highlightFile != null ? highlightFile.getName() : null;
      }

      if (fileName == null && content instanceof FileContent) {
        fileName = ((FileContent)content).getFile().getName();
      }

      if (!StringUtil.isEmptyOrSpaces(fileName)) {
        return fileName;
      }


      FileType fileType = content.getContentType();
      String ext = fileType != null ? fileType.getDefaultExtension() : null;
      if (StringUtil.isEmptyOrSpaces(ext)) ext = "tmp";

      String name = "";
      if (title != null && windowTitle != null) {
        name = title + "_" + windowTitle;
      }
      else if (title != null || windowTitle != null) {
        name = title != null ? title : windowTitle;
      }
      if (name.length() > 50) name = name.substring(0, 50);

      return PathUtil.suggestFileName(name + "." + ext, true, false);
    }
  }
}
