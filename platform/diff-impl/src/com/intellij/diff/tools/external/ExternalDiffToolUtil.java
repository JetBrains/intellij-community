/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.external;

import com.intellij.diff.contents.*;
import com.intellij.diff.merge.MergeResult;
import com.intellij.diff.merge.ThreesideMergeRequest;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineTokenizer;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LineSeparator;
import com.intellij.util.PathUtil;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class ExternalDiffToolUtil {
  public static boolean canCreateFile(@NotNull DiffContent content) {
    if (content instanceof EmptyContent) return true;
    if (content instanceof DocumentContent) return true;
    if (content instanceof FileContent) return true;
    if (content instanceof DirectoryContent) return ((DirectoryContent)content).getFile().isInLocalFileSystem();
    return false;
  }

  @NotNull
  private static InputFile createFile(@NotNull DiffContent content, @Nullable String title, @Nullable String windowTitle)
    throws IOException {

    if (content instanceof EmptyContent) {
      return new TempInputFile(createFile(new byte[0], "empty"));
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

      String tempFileName = getFileName(title, windowTitle, content.getContentType());
      return new TempInputFile(createTempFile(file, tempFileName));
    }
    else if (content instanceof DocumentContent) {
      String tempFileName = getFileName(title, windowTitle, content.getContentType());
      return new TempInputFile(createTempFile((DocumentContent)content, tempFileName));
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
  private static File createTempFile(@NotNull final DocumentContent content, @NotNull String tempFileName) throws IOException {
    FileDocumentManager.getInstance().saveDocument(content.getDocument());

    LineSeparator separator = content.getLineSeparator();
    if (separator == null) separator = LineSeparator.getSystemLineSeparator();

    Charset charset = content.getCharset();
    if (charset == null) charset = Charset.defaultCharset();

    String contentData = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return content.getDocument().getText();
      }
    });
    if (separator != LineSeparator.LF) {
      contentData = StringUtil.convertLineSeparators(contentData, separator.getSeparatorString());
    }

    byte[] bytes = contentData.getBytes(charset);
    return createFile(bytes, tempFileName);
  }

  @NotNull
  private static File createTempFile(@NotNull VirtualFile file, @NotNull String tempFileName) throws IOException {
    byte[] bytes = file.contentsToByteArray();
    return createFile(bytes, tempFileName);
  }

  @NotNull
  private static OutputFile createOutputFile(@NotNull DiffContent content, @Nullable String windowTitle) throws IOException {
    if (content instanceof FileContent) {
      VirtualFile file = ((FileContent)content).getFile();

      Document document = FileDocumentManager.getInstance().getCachedDocument(file);
      if (document != null) {
        FileDocumentManager.getInstance().saveDocument(document);
      }

      if (file.isInLocalFileSystem()) {
        return new LocalOutputFile(file);
      }

      String tempFileName = getFileName(null, windowTitle, content.getContentType());
      File tempFile = createTempFile(file, tempFileName);
      return new NonLocalOutputFile(file, tempFile);
    }
    else if (content instanceof DocumentContent) {
      String tempFileName = getFileName(null, windowTitle, content.getContentType());
      File tempFile = createTempFile(((DocumentContent)content), tempFileName);
      return new DocumentOutputFile(((DocumentContent)content).getDocument(), ((DocumentContent)content).getCharset(), tempFile);
    }
    throw new IllegalArgumentException(content.toString());
  }

  @NotNull
  private static String getFileName(@Nullable String title, @Nullable String windowTitle, @Nullable FileType fileType) {
    String prefix = "";
    if (title != null && windowTitle != null) {
      prefix = title + "_" + windowTitle;
    }
    else if (title != null || windowTitle != null) {
      prefix = title != null ? title : windowTitle;
    }
    // TODO: keep file name in DiffContent ?
    String ext = fileType != null ? fileType.getDefaultExtension() : "tmp";
    if (prefix.length() > 50) prefix = prefix.substring(0, 50);
    return PathUtil.suggestFileName(prefix + "." + ext, true, false);
  }

  @NotNull
  private static File createFile(@NotNull byte[] bytes, @NotNull String name) throws IOException {
    File tempFile = FileUtil.createTempFile("tmp_", "_" + name, true);
    FileUtil.writeToFile(tempFile, bytes);
    return tempFile;
  }

  public static void execute(@NotNull ExternalDiffSettings settings,
                             @NotNull List<? extends DiffContent> contents,
                             @NotNull List<String> titles,
                             @Nullable String windowTitle)
    throws IOException, ExecutionException {
    assert contents.size() == 2 || contents.size() == 3;
    assert titles.size() == contents.size();

    List<InputFile> files = new ArrayList<InputFile>();
    for (int i = 0; i < contents.size(); i++) {
      files.add(createFile(contents.get(i), titles.get(i), windowTitle));
    }

    CommandLineTokenizer parameterTokenizer = new CommandLineTokenizer(settings.getDiffParameters(), true);

    List<String> args = new ArrayList<String>();
    while (parameterTokenizer.hasMoreTokens()) {
      String arg = parameterTokenizer.nextToken();
      if ("%1".equals(arg)) {
        args.add(files.get(0).getPath());
      }
      else if ("%2".equals(arg)) {
        if (files.size() == 3) {
          args.add(files.get(2).getPath());
        }
        else {
          args.add(files.get(1).getPath());
        }
      }
      else if ("%3".equals(arg)) {
        if (files.size() == 3) args.add(files.get(1).getPath());
      }
      else {
        args.add(arg);
      }
    }

    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(settings.getDiffExePath());

    commandLine.addParameters(args);
    commandLine.createProcess();
  }

  public static void executeMerge(@Nullable Project project,
                                  @NotNull ExternalDiffSettings settings,
                                  @NotNull ThreesideMergeRequest request)
    throws IOException, ExecutionException {
    boolean success = false;
    OutputFile outputFile = null;
    List<InputFile> inputFiles = new ArrayList<InputFile>();
    try {
      DiffContent outputContent = request.getOutputContent();
      List<? extends DiffContent> contents = request.getContents();
      List<String> titles = request.getContentTitles();
      String windowTitle = request.getTitle();

      assert contents.size() == 3;
      assert titles.size() == contents.size();

      for (int i = 0; i < contents.size(); i++) {
        inputFiles.add(createFile(contents.get(i), titles.get(i), windowTitle));
      }

      outputFile = createOutputFile(outputContent, windowTitle);

      CommandLineTokenizer parameterTokenizer = new CommandLineTokenizer(settings.getMergeParameters(), true);

      List<String> args = new ArrayList<String>();
      while (parameterTokenizer.hasMoreTokens()) {
        String arg = parameterTokenizer.nextToken();
        if ("%1".equals(arg)) {
          args.add(inputFiles.get(0).getPath());
        }
        else if ("%2".equals(arg)) {
          args.add(inputFiles.get(2).getPath());
        }
        else if ("%3".equals(arg)) {
          args.add(inputFiles.get(1).getPath());
        }
        else if ("%4".equals(arg)) {
          args.add(outputFile.getPath());
        }
        else {
          args.add(arg);
        }
      }

      GeneralCommandLine commandLine = new GeneralCommandLine();
      commandLine.setExePath(settings.getMergeExePath());

      commandLine.addParameters(args);
      final Process process = commandLine.createProcess();

      if (settings.isMergeTrustExitCode()) {
        final Ref<Boolean> resultRef = new Ref<Boolean>();

        ProgressManager.getInstance().run(new Task.Modal(project, "Waiting for external tool", true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
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
                indicator.checkCanceled();
                if (semaphore.tryAcquire(200, TimeUnit.MILLISECONDS)) break;
              }
            }
            catch (InterruptedException ignore) {
            }
            finally {
              waiter.interrupt();
            }
          }
        });

        success = resultRef.get() == Boolean.TRUE;
      }
      else {
        ProgressManager.getInstance().run(new Task.Modal(project, "Launching external tool", false) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            indicator.setIndeterminate(true);
            TimeoutUtil.sleep(1000);
          }
        });

        success = Messages.showYesNoDialog(project,
                                           "Press \"Mark as Resolved\" when you finish resolving conflicts in the external tool",
                                           "Merge In External Tool", "Mark as Resolved", "Revert", null) == Messages.YES;
      }

      if (success) outputFile.apply();
    }
    finally {
      request.applyResult(success ? MergeResult.RESOLVED : MergeResult.CANCEL);

      if (outputFile != null) outputFile.cleanup();
      for (InputFile file : inputFiles) {
        file.cleanup();
      }
    }
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
    public LocalOutputFile(@NotNull VirtualFile file) {
      super(file);
    }

    @Override
    public void apply() {
      myFile.refresh(false, false);
    }
  }

  private static class NonLocalOutputFile extends TempInputFile implements OutputFile {
    @NotNull private final VirtualFile myFile;

    public NonLocalOutputFile(@NotNull VirtualFile file, @NotNull File localFile) {
      super(localFile);
      myFile = file;
    }

    @Override
    public void apply() throws IOException {
      myFile.setBinaryContent(FileUtil.loadFileBytes(myLocalFile));
    }
  }

  private static class DocumentOutputFile extends TempInputFile implements OutputFile {
    @NotNull private final Document myDocument;
    @NotNull private final Charset myCharset;

    public DocumentOutputFile(@NotNull Document document, @Nullable Charset charset, @NotNull File localFile) {
      super(localFile);
      myDocument = document;
      // TODO: potentially dangerous operation - we're using default charset
      myCharset = charset != null ? charset : Charset.defaultCharset();
    }

    @Override
    public void apply() throws IOException {
      final String content = StringUtil.convertLineSeparators(FileUtil.loadFile(myLocalFile, myCharset));
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          myDocument.setText(content);
        }
      });
    }
  }

  private static class LocalInputFile implements InputFile {
    @NotNull protected final VirtualFile myFile;

    public LocalInputFile(@NotNull VirtualFile file) {
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

    public TempInputFile(@NotNull File localFile) {
      myLocalFile = localFile;
    }

    @NotNull
    @Override
    public String getPath() {
      return myLocalFile.getPath();
    }

    @Override
    public void cleanup() {
      myLocalFile.delete();
    }
  }
}
