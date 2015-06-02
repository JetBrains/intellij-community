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

public class ExternalDiffToolUtil {
  public static boolean canCreateFile(@NotNull DiffContent content) {
    if (content instanceof EmptyContent) return true;
    if (content instanceof DocumentContent) return true;
    if (content instanceof FileContent) return true;
    if (content instanceof DirectoryContent) return ((DirectoryContent)content).getFile().isInLocalFileSystem();
    return false;
  }

  @NotNull
  public static String createFile(@NotNull DiffContent content, @Nullable String title, @Nullable String windowTitle)
    throws IOException {

    if (content instanceof EmptyContent) {
      return createFile(new byte[0], "empty").getPath();
    }
    else if (content instanceof FileContent) {
      VirtualFile file = ((FileContent)content).getFile();
      if (file.isInLocalFileSystem()) {
        return file.getPath();
      }

      String tempFileName = getFileName(title, windowTitle, content.getContentType());
      return createTempFile(file, tempFileName).getPath();
    }
    else if (content instanceof DocumentContent) {
      String tempFileName = getFileName(title, windowTitle, content.getContentType());
      return createTempFile(((DocumentContent)content), tempFileName).getPath();
    }
    else if (content instanceof DirectoryContent) {
      VirtualFile file = ((DirectoryContent)content).getFile();
      if (file.isInLocalFileSystem()) {
        return file.getPath();
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
  public static OutputFile createOutputFile(@NotNull DiffContent content, @Nullable String windowTitle) throws IOException {
    if (content instanceof FileContent) {
      FileContent fileContent = (FileContent)content;
      if (fileContent.getFile().isInLocalFileSystem()) {
        return new LocalOutputFile(fileContent.getFile());
      }

      String tempFileName = getFileName(null, windowTitle, content.getContentType());
      File tempFile = createTempFile(fileContent.getFile(), tempFileName);
      return new NonLocalOutputFile(fileContent.getFile(), tempFile);
    }
    else if (content instanceof DocumentContent) {
      String tempFileName = getFileName(null, windowTitle, content.getContentType());
      File tempFile = createTempFile(((DocumentContent)content), tempFileName);
      return new DocumentOutputFile(((DocumentContent)content).getDocument(), ((DocumentContent)content).getCharset(), tempFile);
    }
    throw new IllegalArgumentException(content.toString());
  }

  @NotNull
  public static String getFileName(@Nullable String title, @Nullable String windowTitle, @Nullable FileType fileType) {
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
  public static File createFile(@NotNull byte[] bytes, @NotNull String name) throws IOException {
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

    List<String> files = new ArrayList<String>();
    for (int i = 0; i < contents.size(); i++) {
      files.add(createFile(contents.get(i), titles.get(i), windowTitle));
    }

    CommandLineTokenizer parameterTokenizer = new CommandLineTokenizer(settings.getDiffParameters(), true);

    List<String> args = new ArrayList<String>();
    while (parameterTokenizer.hasMoreTokens()) {
      String arg = parameterTokenizer.nextToken();
      if ("%1".equals(arg)) {
        args.add(files.get(0));
      }
      else if ("%2".equals(arg)) {
        if (files.size() == 3) {
          args.add(files.get(2));
        }
        else {
          args.add(files.get(1));
        }
      }
      else if ("%3".equals(arg)) {
        if (files.size() == 3) args.add(files.get(1));
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
    DiffContent outputContent = request.getOutputContent();
    List<? extends DiffContent> contents = request.getContents();
    List<String> titles = request.getContentTitles();
    String windowTitle = request.getTitle();

    assert contents.size() == 3;
    assert titles.size() == contents.size();

    List<String> files = new ArrayList<String>();
    for (int i = 0; i < contents.size(); i++) {
      files.add(createFile(contents.get(i), titles.get(i), windowTitle));
    }

    OutputFile outputFile = createOutputFile(outputContent, windowTitle);

    CommandLineTokenizer parameterTokenizer = new CommandLineTokenizer(settings.getMergeParameters(), true);

    List<String> args = new ArrayList<String>();
    while (parameterTokenizer.hasMoreTokens()) {
      String arg = parameterTokenizer.nextToken();
      if ("%1".equals(arg)) {
        args.add(files.get(0));
      }
      else if ("%2".equals(arg)) {
        args.add(files.get(2));
      }
      else if ("%3".equals(arg)) {
        args.add(files.get(1));
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
    commandLine.createProcess();

    ProgressManager.getInstance().run(new Task.Modal(project, "Launching external tool", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        TimeoutUtil.sleep(1000);
      }
    });

    // TODO: respect exit code of process
    if (Messages.showYesNoDialog(project,
                                 "Press \"Mark as Resolved\" when you finish resolving conflicts in the external tool",
                                 "Merge In External Tool", "Mark as Resolved", "Revert", null) != Messages.YES) {
      request.applyResult(MergeResult.CANCEL);
      return;
    }

    outputFile.finish();
    request.applyResult(MergeResult.RESOLVED);
  }

  //
  // Helpers
  //

  private interface OutputFile {
    @NotNull
    String getPath();

    void finish() throws IOException;
  }

  private static class LocalOutputFile implements OutputFile {
    @NotNull private final VirtualFile myFile;

    public LocalOutputFile(@NotNull VirtualFile file) {
      myFile = file;
    }

    @NotNull
    @Override
    public String getPath() {
      return myFile.getPath();
    }

    @Override
    public void finish() {
      myFile.refresh(false, false);
    }
  }

  private static class NonLocalOutputFile implements OutputFile {
    @NotNull private final VirtualFile myFile;
    @NotNull private final File myLocalFile;

    public NonLocalOutputFile(@NotNull VirtualFile file, @NotNull File localFile) {
      myFile = file;
      myLocalFile = localFile;
    }

    @NotNull
    @Override
    public String getPath() {
      return myLocalFile.getPath();
    }

    @Override
    public void finish() throws IOException {
      myFile.setBinaryContent(FileUtil.loadFileBytes(myLocalFile));
    }
  }

  private static class DocumentOutputFile implements OutputFile {
    @NotNull private final Document myDocument;
    @NotNull private final File myLocalFile;
    @NotNull private final Charset myCharset;

    public DocumentOutputFile(@NotNull Document document, @Nullable Charset charset, @NotNull File localFile) {
      myDocument = document;
      myLocalFile = localFile;
      // TODO: potentially dangerous operation - we're using default charset
      myCharset = charset != null ? charset : Charset.defaultCharset();
    }

    @NotNull
    @Override
    public String getPath() {
      return myLocalFile.getPath();
    }

    @Override
    public void finish() throws IOException {
      final String content = StringUtil.convertLineSeparators(FileUtil.loadFile(myLocalFile, myCharset));
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          myDocument.setText(content);
        }
      });
    }
  }
}
