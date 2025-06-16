// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.external;

import com.intellij.CommonBundle;
import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.contents.*;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.merge.MergeResult;
import com.intellij.diff.merge.ThreesideMergeRequest;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.diff.util.ThreeSide;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.DiffUsageTriggerCollector;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.*;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public final class ExternalDiffToolUtil {
  private static final Logger LOG = Logger.getInstance(ExternalDiffToolUtil.class);

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

  private static @NotNull InputFile createFile(@Nullable Project project, @NotNull DiffContent content, @NotNull FileNameInfo fileName)
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

  private static @NotNull File createTempFile(@Nullable Project project,
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

  private static @NotNull File createTempFile(@NotNull VirtualFile file, @NotNull FileNameInfo fileName) throws IOException {
    byte[] bytes = file.contentsToByteArray();
    return createFile(bytes, fileName);
  }

  private static @NotNull Charset getContentCharset(@Nullable Project project, @NotNull DocumentContent content) {
    Charset charset = content.getCharset();
    if (charset != null) return charset;
    EncodingManager e = project != null ? EncodingProjectManager.getInstance(project) : EncodingManager.getInstance();
    return e.getDefaultCharset();
  }

  private static @NotNull OutputFile createOutputFile(@Nullable Project project,
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
    else if (content instanceof DocumentContent documentContent) {
      File tempFile = createTempFile(project, documentContent, fileName);
      Charset charset = getContentCharset(project, documentContent);
      return new DocumentOutputFile(documentContent.getDocument(), charset, tempFile);
    }
    throw new IllegalArgumentException(content.toString());
  }

  private static @NotNull File createFile(byte @NotNull [] bytes, @NotNull FileNameInfo fileName) throws IOException {
    File tempFile = FileUtil.createTempFile(fileName.prefix + "_", "_" + fileName.name, true);
    FileUtil.writeToFile(tempFile, bytes);
    return tempFile;
  }

  public static void testDiffTool2(@Nullable Project project,
                                   @NotNull ExternalDiffSettings.ExternalTool externalTool,
                                   @NotNull TestOutputConsole outputConsole) {
    DiffContentFactory factory = DiffContentFactory.getInstance();
    List<? extends DiffContent> contents =
      Arrays.asList(factory.create(DiffBundle.message("settings.external.diff.left.file.content"), FileTypes.PLAIN_TEXT),
                    factory.create(DiffBundle.message("settings.external.diff.right.file.content"), FileTypes.PLAIN_TEXT));
    List<String> titles = Arrays.asList("Left", "Right"); // NON-NLS
    testDiffTool(project, externalTool, contents, titles, outputConsole);
  }

  public static void testDiffTool3(@Nullable Project project,
                                   @NotNull ExternalDiffSettings.ExternalTool externalTool,
                                   @NotNull TestOutputConsole outputConsole) {
    DiffContentFactory factory = DiffContentFactory.getInstance();
    List<? extends DiffContent> contents =
      Arrays.asList(factory.create(DiffBundle.message("settings.external.diff.left.file.content"), FileTypes.PLAIN_TEXT),
                    factory.create(DiffBundle.message("settings.external.diff.base.file.content"), FileTypes.PLAIN_TEXT),
                    factory.create(DiffBundle.message("settings.external.diff.right.file.content"), FileTypes.PLAIN_TEXT));
    List<String> titles = Arrays.asList("Left", "Base", "Right"); // NON-NLS
    testDiffTool(project, externalTool, contents, titles, outputConsole);
  }

  private static void testDiffTool(@Nullable Project project,
                                   @NotNull ExternalDiffSettings.ExternalTool externalTool,
                                   @NotNull List<? extends DiffContent> contents,
                                   @NotNull List<String> titles,
                                   @NotNull TestOutputConsole outputConsole) {
    JComponent parentComponent = outputConsole.getComponent();
    try {
      GeneralCommandLine commandLine = createDiffCommandLine(project, externalTool, contents, titles, null);

      KillableProcessHandler processHandler = new TestKillableProcessHandler(commandLine);
      addLoggingListener(outputConsole, commandLine, processHandler);
      processHandler.startNotify();
    }
    catch (Exception e) {
      Messages.showErrorDialog(parentComponent, e.getMessage(), DiffBundle.message("error.cannot.show.diff"));
    }
  }

  public static void testMergeTool(@Nullable Project project,
                                   @NotNull ExternalDiffSettings.ExternalTool externalTool,
                                   @NotNull TestOutputConsole outputConsole) {
    JComponent parentComponent = outputConsole.getComponent();
    try {
      Document document = new DocumentImpl(DiffBundle.message("settings.external.diff.original.output.file.content"));

      Consumer<? super MergeResult> callback = (result) -> {
        String message = result == MergeResult.CANCEL ? DiffBundle.message("settings.external.diff.merge.conflict.resolve.was.canceled")
                                                      : DiffBundle.message("settings.external.diff.merge.conflict.resolve.successful",
                                                                           StringUtil.shortenPathWithEllipsis(document.getText(), 60));
        Messages.showInfoMessage(message, DiffBundle.message("settings.external.diff.test.complete"));
      };

      List<String> contents =
        Arrays.asList(DiffBundle.message("settings.external.diff.left.file.content"),
                      DiffBundle.message("settings.external.diff.base.file.content"),
                      DiffBundle.message("settings.external.diff.right.file.content"));
      List<String> titles = Arrays.asList("Left", "Base", "Right"); // NON-NLS

      MergeRequest request = DiffRequestFactory.getInstance()
        .createMergeRequest(null, PlainTextFileType.INSTANCE, document, contents, null, titles, callback);

      handleMergeRequest(request, () -> {
        return runWithTempMergeFiles(project, (ThreesideMergeRequest)request, (outputFile, inputFiles) -> {
          try {
            GeneralCommandLine commandLine = createMergeCommandLine(externalTool, outputFile, inputFiles);

            KillableProcessHandler processHandler = new TestKillableProcessHandler(commandLine);
            addLoggingListener(outputConsole, commandLine, processHandler);
            processHandler.startNotify();

            return waitMergeProcessWithModal(project, processHandler.getProcess(), externalTool.isMergeTrustExitCode(),
                                             parentComponent);
          }
          catch (ExecutionException e) {
            throw new IOException(e);
          }
        });
      });
    }
    catch (Exception e) {
      Messages.showErrorDialog(parentComponent, e.getMessage(), DiffBundle.message("error.cannot.show.merge"));
    }
  }

  private static void addLoggingListener(@NotNull TestOutputConsole outputConsole,
                                         GeneralCommandLine commandLine,
                                         @NotNull ProcessHandler processHandler) {
    outputConsole.appendOutput(ProcessOutputTypes.SYSTEM, commandLine.getCommandLineString());

    processHandler.addProcessListener(new ProcessListener() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        if (outputType == ProcessOutputTypes.STDOUT ||
            outputType == ProcessOutputTypes.STDERR) {
          outputConsole.appendOutput(outputType, event.getText());
        }
      }

      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        outputConsole.processTerminated(event.getExitCode());
      }
    });
  }

  public static void executeDiff(@Nullable Project project,
                                 @NotNull ExternalDiffSettings.ExternalTool externalTool,
                                 @NotNull List<? extends DiffContent> contents,
                                 @NotNull List<String> titles,
                                 @Nullable String windowTitle) throws IOException {
    try {
      DiffUsageTriggerCollector.logShowExternalTool(project, false);

      GeneralCommandLine commandLine = createDiffCommandLine(project, externalTool, contents, titles, windowTitle);
      commandLine.createProcess();
    }
    catch (ExecutionException e) {
      throw new IOException(e);
    }
  }

  private static @NotNull GeneralCommandLine createDiffCommandLine(@Nullable Project project,
                                                                   @NotNull ExternalDiffSettings.ExternalTool externalTool,
                                                                   @NotNull List<? extends DiffContent> contents,
                                                                   @NotNull List<String> titles,
                                                                   @Nullable String windowTitle) throws IOException {
    assert contents.size() == 2 || contents.size() == 3;
    assert titles.size() == contents.size();

    // Do not clean up - we do not know when the tool is terminated
    List<InputFile> files = createInputFiles(project, contents, titles, windowTitle);

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

    return createCommandLine(externalTool.getProgramPath(), externalTool.getArgumentPattern(), patterns);
  }

  @RequiresEdt
  public static void executeMerge(@Nullable Project project,
                                  @NotNull ExternalDiffSettings.ExternalTool externalTool,
                                  @NotNull ThreesideMergeRequest request,
                                  @Nullable JComponent parentComponent) throws IOException {
    DiffUsageTriggerCollector.logShowExternalTool(project, true);

    handleMergeRequest(request, () -> tryExecuteMerge(project, externalTool, request, parentComponent));
  }

  private static void handleMergeRequest(@NotNull MergeRequest request,
                                         @NotNull ThrowableComputable<Boolean, IOException> mergeTask) throws IOException {
    request.onAssigned(true);
    try {
      boolean success = false;
      try {
        success = mergeTask.compute();
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
                                        @Nullable JComponent parentComponent) throws IOException {
    return runWithTempMergeFiles(project, request, (outputFile, inputFiles) -> {
      GeneralCommandLine commandLine = createMergeCommandLine(externalTool, outputFile, inputFiles);
      try {
        Process process = commandLine.createProcess();
        return waitMergeProcessWithModal(project, process, externalTool.isMergeTrustExitCode(), parentComponent);
      }
      catch (ExecutionException e) {
        throw new IOException(e);
      }
    });
  }

  private static boolean waitMergeProcessWithModal(@Nullable Project project,
                                                   @NotNull Process process,
                                                   boolean trustExitCode,
                                                   @Nullable JComponent parentComponent) {
    if (trustExitCode) {
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
      return resultRef.get() == Boolean.TRUE;
    }
    else {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        TimeoutUtil.sleep(1000);
      }, DiffBundle.message("launching.external.tool"), false, project, parentComponent);

      return Messages.showYesNoDialog(project,
                                      DiffBundle.message("press.mark.as.resolve"),
                                      DiffBundle.message("merge.in.external.tool"),
                                      DiffBundle.message("mark.as.resolved"),
                                      CommonBundle.message("button.revert"), null) == Messages.YES;
    }
  }

  private static boolean runWithTempMergeFiles(@Nullable Project project,
                                               @NotNull ThreesideMergeRequest request,
                                               @NotNull MergeTask mergeTask) throws IOException {
    OutputFile outputFile = null;
    List<InputFile> inputFiles = null;
    try {
      outputFile = createOutputFile(project, request.getOutputContent(), request.getTitle());
      inputFiles = createInputFiles(project, request.getContents(), request.getContentTitles(), request.getTitle());

      boolean success = mergeTask.runMerge(outputFile, inputFiles);
      if (success) {
        outputFile.apply();
      }
      return success;
    }
    finally {
      if (outputFile != null) outputFile.cleanup();
      if (inputFiles != null) {
        for (InputFile file : inputFiles) {
          file.cleanup();
        }
      }
    }
  }

  private static @NotNull GeneralCommandLine createMergeCommandLine(@NotNull ExternalDiffSettings.ExternalTool externalTool,
                                                                    @NotNull OutputFile outputFile,
                                                                    @NotNull List<? extends InputFile> inputFiles) {
    assert inputFiles.size() == 3;

    Map<String, String> patterns = new HashMap<>();
    patterns.put("%1", inputFiles.get(0).getPath());
    patterns.put("%2", inputFiles.get(2).getPath());
    patterns.put("%3", inputFiles.get(1).getPath());
    patterns.put("%4", outputFile.getPath());

    return createCommandLine(externalTool.getProgramPath(), externalTool.getArgumentPattern(), patterns);
  }

  private static @NotNull GeneralCommandLine createCommandLine(@NotNull String exePath,
                                                               @NotNull String parametersTemplate,
                                                               @NotNull Map<String, String> patterns) {
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
    return commandLine;
  }

  private static @NotNull List<InputFile> createInputFiles(@Nullable Project project,
                                                           @NotNull List<? extends DiffContent> contents,
                                                           @NotNull List<String> titles,
                                                           @Nullable String windowTitle) throws IOException {
    List<InputFile> inputFiles = new ArrayList<>();
    for (int i = 0; i < contents.size(); i++) {
      DiffContent content = contents.get(i);
      FileNameInfo fileName = FileNameInfo.create(contents, titles, windowTitle, i);
      inputFiles.add(createFile(project, content, fileName));
    }
    return inputFiles;
  }

  private static @NotNull OutputFile createOutputFile(@Nullable Project project,
                                                      @NotNull DiffContent outputContent,
                                                      @Nullable String windowTitle) throws IOException {
    return createOutputFile(project, outputContent, FileNameInfo.createMergeResult(outputContent, windowTitle));
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
    private final @NotNull VirtualFile myFile;

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
    private final @NotNull Document myDocument;
    private final @NotNull Charset myCharset;

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
    protected final @NotNull VirtualFile myFile;

    LocalInputFile(@NotNull VirtualFile file) {
      myFile = file;
    }

    @Override
    public @NotNull String getPath() {
      return myFile.getPath();
    }

    @Override
    public void cleanup() {
    }
  }

  private static class TempInputFile implements InputFile {
    protected final @NotNull File myLocalFile;

    TempInputFile(@NotNull File localFile) {
      myLocalFile = localFile;
    }

    @Override
    public @NotNull String getPath() {
      return myLocalFile.getPath();
    }

    @Override
    public void cleanup() {
      FileUtil.delete(myLocalFile);
    }
  }

  private static class FileNameInfo {
    public final @NotNull String prefix;
    public final @NotNull String name;

    FileNameInfo(@NotNull @NonNls String prefix, @NotNull @NonNls String name) {
      this.prefix = prefix;
      this.name = name;
    }

    public static @NotNull FileNameInfo create(@NotNull List<? extends DiffContent> contents,
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

    public static @NotNull FileNameInfo createMergeResult(@NotNull DiffContent content, @Nullable String windowTitle) {
      String name = getFileName(content, null, windowTitle);
      return new FileNameInfo("merge_result", name);
    }

    private static @NotNull String getFileName(@NotNull DiffContent content,
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

  private interface MergeTask {
    boolean runMerge(@NotNull OutputFile outputFile, @NotNull List<InputFile> inputFiles) throws IOException;
  }

  private static class TestKillableProcessHandler extends KillableProcessHandler {
    private static final BaseOutputReader.Options FULL_LINES_READER_OPTIONS = new BaseOutputReader.Options() {
      @Override
      public BaseDataReader.SleepingPolicy policy() { return BaseDataReader.SleepingPolicy.NON_BLOCKING; }

      @Override
      public boolean sendIncompleteLines() { return false; }
    };

    TestKillableProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
      super(commandLine);
    }

    @Override
    protected @NotNull BaseOutputReader.Options readerOptions() {
      return FULL_LINES_READER_OPTIONS;
    }
  }

  public interface TestOutputConsole {
    @NotNull JComponent getComponent();

    void appendOutput(@NotNull Key<?> outputType, @NotNull String line);

    void processTerminated(int exitCode);
  }
}
