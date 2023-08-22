// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff;

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.merge.MergeCallback;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.merge.MergeResult;
import com.intellij.diff.merge.TextMergeRequest;
import com.intellij.diff.requests.BinaryMergeRequestImpl;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.requests.TextMergeRequestImpl;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.LocalFilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.intellij.util.ArrayUtilRt.EMPTY_BYTE_ARRAY;
import static com.intellij.util.ObjectUtils.chooseNotNull;
import static com.intellij.util.ObjectUtils.notNull;

public class DiffRequestFactoryImpl extends DiffRequestFactory {
  public static final @NlsSafe String DIFF_TITLE_SEPARATOR = " - ";
  public static final @NlsSafe String DIFF_TITLE_RENAME_SEPARATOR = " -> ";

  private final DiffContentFactoryEx myContentFactory = DiffContentFactoryEx.getInstanceEx();

  //
  // Diff
  //

  @NotNull
  @Override
  public ContentDiffRequest createFromFiles(@Nullable Project project, @Nullable VirtualFile file1, @Nullable VirtualFile file2) {
    assert file1 != null || file2 != null;

    DiffContent content1 = file1 != null ? myContentFactory.create(project, file1) : myContentFactory.createEmpty();
    DiffContent content2 = file2 != null ? myContentFactory.create(project, file2) : myContentFactory.createEmpty();

    String title1 = getContentTitle(file1);
    String title2 = getContentTitle(file2);

    String title = getTitle(file1, file2);

    return new SimpleDiffRequest(title, content1, content2, title1, title2);
  }

  @NotNull
  @Override
  public ContentDiffRequest createFromFiles(@Nullable Project project,
                                            @NotNull VirtualFile leftFile,
                                            @NotNull VirtualFile baseFile,
                                            @NotNull VirtualFile rightFile) {
    DiffContent content1 = myContentFactory.create(project, leftFile);
    DiffContent content2 = myContentFactory.create(project, baseFile);
    DiffContent content3 = myContentFactory.create(project, rightFile);

    String title1 = getContentTitle(leftFile);
    String title2 = getContentTitle(baseFile);
    String title3 = getContentTitle(rightFile);

    return new SimpleDiffRequest(null, content1, content2, content3, title1, title2, title3);
  }

  @NotNull
  @Override
  public ContentDiffRequest createClipboardVsValue(@NotNull String value) {
    DiffContent content1 = myContentFactory.createClipboardContent();
    DiffContent content2 = myContentFactory.create(value);

    String title1 = DiffBundle.message("diff.content.clipboard.content.title");
    String title2 = DiffBundle.message("diff.content.selected.value");

    String title = DiffBundle.message("diff.clipboard.vs.value.dialog.title");

    return new SimpleDiffRequest(title, content1, content2, title1, title2);
  }

  //
  // Titles
  //

  @Nullable
  @Override
  public String getContentTitle(@Nullable VirtualFile file) {
    if (file == null) return null;
    return getContentTitle(new LocalFilePath(file.getPath(), file.isDirectory()));
  }

  @NotNull
  @Override
  public String getTitle(@Nullable VirtualFile file1, @Nullable VirtualFile file2) {
    FilePath path1 = file1 != null ? new LocalFilePath(file1.getPath(), file1.isDirectory()) : null;
    FilePath path2 = file2 != null ? new LocalFilePath(file2.getPath(), file2.isDirectory()) : null;
    return getTitle(path1, path2, DIFF_TITLE_SEPARATOR);
  }

  @NotNull
  @Override
  public String getTitle(@NotNull VirtualFile file) {
    return getTitle(file, null);
  }

  @Nls
  @NotNull
  public static String getContentTitle(@NotNull FilePath path) {
    if (path.isDirectory()) return path.getPresentableUrl();
    FilePath parent = path.getParentPath();
    return getContentTitle(path.getName(), path.getPresentableUrl(), parent != null ? parent.getPresentableUrl() : null);
  }

  @Nls
  @NotNull
  public static String getTitle(@Nullable FilePath path1, @Nullable FilePath path2, @NotNull @Nls String separator) {
    assert path1 != null || path2 != null;

    if (path1 == null || path2 == null) {
      return getContentTitle(chooseNotNull(path1, path2));
    }

    if ((path1.isDirectory() || path2.isDirectory()) && path1.getPath().equals(path2.getPath())) {
      return path1.getPresentableUrl();
    }

    String name1 = path1.getName();
    String name2 = path2.getName();

    if (path1.isDirectory() ^ path2.isDirectory()) {
      if (path1.isDirectory()) name1 += File.separatorChar;
      if (path2.isDirectory()) name2 += File.separatorChar;
    }

    FilePath parent1 = path1.getParentPath();
    FilePath parent2 = path2.getParentPath();
    return getRequestTitle(name1, path1.getPresentableUrl(), parent1 != null ? parent1.getPresentableUrl() : null,
                           name2, path2.getPresentableUrl(), parent2 != null ? parent2.getPresentableUrl() : null,
                           separator);
  }

  @Nls
  @NotNull
  private static String getContentTitle(@NotNull @Nls String name, @NotNull @Nls String path, @Nullable @Nls String parentPath) {
    if (parentPath != null) {
      return name + " (" + parentPath + ")";
    }
    else {
      return path;
    }
  }

  @Nls
  @NotNull
  private static String getRequestTitle(@NotNull @Nls String name1, @NotNull @Nls String path1, @Nullable @Nls String parentPath1,
                                        @NotNull @Nls String name2, @NotNull @Nls String path2, @Nullable @Nls String parentPath2,
                                        @NotNull @Nls String sep) {
    if (path1.equals(path2)) return getContentTitle(name1, path1, parentPath1);

    if (Objects.equals(parentPath1, parentPath2)) {
      if (parentPath1 != null) {
        return name1 + sep + name2 + " (" + parentPath1 + ")";
      }
      else {
        return path1 + sep + path2;
      }
    }
    else {
      if (name1.equals(name2)) {
        if (parentPath1 != null && parentPath2 != null) {
          return name1 + " (" + parentPath1 + sep + parentPath2 + ")";
        }
        else {
          return path1 + sep + path2;
        }
      }
      else {
        if (parentPath1 != null && parentPath2 != null) {
          return name1 + sep + name2 + " (" + parentPath1 + sep + parentPath2 + ")";
        }
        else {
          return path1 + sep + path2;
        }
      }
    }
  }

  //
  // Merge
  //

  @NotNull
  @Override
  public MergeRequest createMergeRequest(@Nullable Project project,
                                         @Nullable FileType fileType,
                                         @NotNull Document outputDocument,
                                         @NotNull List<String> textContents,
                                         @Nullable @NlsContexts.DialogTitle String title,
                                         @NotNull List<@Nls String> titles,
                                         @Nullable Consumer<? super MergeResult> applyCallback) throws InvalidDiffRequestException {
    if (textContents.size() != 3) throw new IllegalArgumentException();
    if (titles.size() != 3) throw new IllegalArgumentException();

    if (!DiffUtil.canMakeWritable(outputDocument)) throw new InvalidDiffRequestException("Output is read only");

    DocumentContent outputContent = myContentFactory.create(project, outputDocument, fileType);
    CharSequence originalContent = outputDocument.getImmutableCharSequence();

    List<DocumentContent> contents = new ArrayList<>(3);
    for (String text : textContents) {
      contents.add(myContentFactory.create(project, text, fileType));
    }

    TextMergeRequestImpl request = new TextMergeRequestImpl(project, outputContent, originalContent, contents, title, titles);
    return MergeCallback.register(request, applyCallback);
  }

  @NotNull
  @Override
  public MergeRequest createMergeRequest(@Nullable Project project,
                                         @NotNull VirtualFile output,
                                         @NotNull List<byte[]> byteContents,
                                         @Nullable @NlsContexts.DialogTitle String title,
                                         @NotNull List<@Nls String> contentTitles,
                                         @Nullable Consumer<? super MergeResult> applyCallback) throws InvalidDiffRequestException {
    MergeRequest request = createMergeRequest(project, output, byteContents, title, contentTitles);
    return MergeCallback.register(request, applyCallback);
  }

  @NotNull
  @Override
  public MergeRequest createMergeRequest(@Nullable Project project,
                                         @NotNull VirtualFile output,
                                         @NotNull List<byte[]> byteContents,
                                         @Nullable @NlsContexts.DialogTitle String title,
                                         @NotNull List<@Nls String> contentTitles) throws InvalidDiffRequestException {
    try {
      return createTextMergeRequest(project, output, byteContents, title, contentTitles);
    }
    catch (InvalidDiffRequestException e) {
      return createBinaryMergeRequest(project, output, byteContents, title, contentTitles);
    }
  }

  @NotNull
  @Override
  public TextMergeRequest createTextMergeRequest(@Nullable Project project,
                                                 @NotNull VirtualFile output,
                                                 @NotNull List<byte[]> byteContents,
                                                 @Nullable @NlsContexts.DialogTitle String title,
                                                 @NotNull List<@Nls String> contentTitles,
                                                 @Nullable Consumer<? super MergeResult> applyCallback) throws InvalidDiffRequestException {
    TextMergeRequest request = createTextMergeRequest(project, output, byteContents, title, contentTitles);
    return MergeCallback.register(request, applyCallback);
  }

  @NotNull
  private TextMergeRequest createTextMergeRequest(@Nullable Project project,
                                                  @NotNull VirtualFile output,
                                                  @NotNull List<byte[]> byteContents,
                                                  @Nullable @NlsContexts.DialogTitle String title,
                                                  @NotNull List<@Nls String> contentTitles) throws InvalidDiffRequestException {
    if (byteContents.size() != 3) throw new IllegalArgumentException();
    if (contentTitles.size() != 3) throw new IllegalArgumentException();

    final Document outputDocument = ReadAction.compute(() -> FileDocumentManager.getInstance().getDocument(output));
    if (outputDocument == null) throw new InvalidDiffRequestException("Can't get output document: " + output.getPresentableUrl());
    if (!DiffUtil.canMakeWritable(outputDocument)) throw new InvalidDiffRequestException("Output is read only: " + output.getPresentableUrl());

    DocumentContent outputContent = myContentFactory.create(project, outputDocument);
    CharSequence originalContent = outputDocument.getImmutableCharSequence();

    List<DocumentContent> contents = new ArrayList<>(3);
    for (byte[] bytes : byteContents) {
      contents.add(myContentFactory.createDocumentFromBytes(project, notNull(bytes, EMPTY_BYTE_ARRAY), output));
    }

    return new TextMergeRequestImpl(project, outputContent, originalContent, contents, title, contentTitles);
  }

  @NotNull
  @Override
  public MergeRequest createBinaryMergeRequest(@Nullable Project project,
                                               @NotNull VirtualFile output,
                                               @NotNull List<byte[]> byteContents,
                                               @Nullable @NlsContexts.DialogTitle String title,
                                               @NotNull List<@Nls String> contentTitles,
                                               @Nullable Consumer<? super MergeResult> applyCallback) throws InvalidDiffRequestException {
    MergeRequest request = createBinaryMergeRequest(project, output, byteContents, title, contentTitles);
    return MergeCallback.register(request, applyCallback);
  }

  @NotNull
  private MergeRequest createBinaryMergeRequest(@Nullable Project project,
                                                @NotNull VirtualFile output,
                                                @NotNull List<byte[]> byteContents,
                                                @Nullable @NlsContexts.DialogTitle String title,
                                                @NotNull List<@Nls String> contentTitles) throws InvalidDiffRequestException {
    if (byteContents.size() != 3) throw new IllegalArgumentException();
    if (contentTitles.size() != 3) throw new IllegalArgumentException();

    try {
      FileContent outputContent = myContentFactory.createFile(project, output);
      if (outputContent == null) throw new InvalidDiffRequestException("Can't process file: " + output);
      byte[] originalContent = ReadAction.compute(() -> output.contentsToByteArray());

      List<DiffContent> contents = new ArrayList<>(3);
      for (byte[] bytes : byteContents) {
        contents.add(myContentFactory.createFromBytes(project, notNull(bytes, EMPTY_BYTE_ARRAY), output));
      }

      return new BinaryMergeRequestImpl(project, outputContent, originalContent, contents, byteContents, title, contentTitles);
    }
    catch (IOException e) {
      throw new InvalidDiffRequestException("Can't read from file", e);
    }
  }

  @NotNull
  @Override
  public MergeRequest createMergeRequestFromFiles(@Nullable Project project,
                                                  @NotNull VirtualFile output,
                                                  @NotNull List<? extends VirtualFile> fileContents,
                                                  @Nullable Consumer<? super MergeResult> applyCallback) throws InvalidDiffRequestException {
    String title = DiffBundle.message("merge.window.title.file", output.getPresentableUrl());
    List<String> titles = Arrays.asList(DiffBundle.message("merge.version.title.our"),
                                        DiffBundle.message("merge.version.title.base"),
                                        DiffBundle.message("merge.version.title.their"));
    return createMergeRequestFromFiles(project, output, fileContents, title, titles, applyCallback);
  }

  @NotNull
  @Override
  public MergeRequest createMergeRequestFromFiles(@Nullable Project project,
                                                  @NotNull VirtualFile output,
                                                  @NotNull List<? extends VirtualFile> fileContents,
                                                  @Nullable String title,
                                                  @NotNull List<String> contentTitles,
                                                  @Nullable Consumer<? super MergeResult> applyCallback) throws InvalidDiffRequestException {
    if (fileContents.size() != 3) throw new IllegalArgumentException();
    if (contentTitles.size() != 3) throw new IllegalArgumentException();

    try {
      return createTextMergeRequestFromFiles(project, output, fileContents, title, contentTitles, applyCallback);
    }
    catch (InvalidDiffRequestException e) {
      return createBinaryMergeRequestFromFiles(project, output, fileContents, title, contentTitles, applyCallback);
    }
  }

  @NotNull
  @Override
  public TextMergeRequest createTextMergeRequestFromFiles(@Nullable Project project,
                                                          @NotNull VirtualFile output,
                                                          @NotNull List<? extends VirtualFile> fileContents,
                                                          @Nullable String title,
                                                          @NotNull List<String> contentTitles,
                                                          @Nullable Consumer<? super MergeResult> applyCallback) throws InvalidDiffRequestException {
    List<byte[]> byteContents = new ArrayList<>(3);
    for (VirtualFile file : fileContents) {
      try {
        byteContents.add(ReadAction.compute(() -> file.contentsToByteArray()));
      }
      catch (IOException e) {
        throw new InvalidDiffRequestException("Can't read from file: " + file.getPresentableUrl(), e);
      }
    }

    return createTextMergeRequest(project, output, byteContents, title, contentTitles, applyCallback);
  }

  @NotNull
  public MergeRequest createBinaryMergeRequestFromFiles(@Nullable Project project,
                                                        @NotNull VirtualFile output,
                                                        @NotNull List<? extends VirtualFile> fileContents,
                                                        @Nullable @NlsContexts.DialogTitle String title,
                                                        @NotNull List<@Nls String> contentTitles,
                                                        @Nullable Consumer<? super MergeResult> applyCallback) throws InvalidDiffRequestException {
    if (fileContents.size() != 3) throw new IllegalArgumentException();
    if (contentTitles.size() != 3) throw new IllegalArgumentException();


    try {
      FileContent outputContent = myContentFactory.createFile(project, output);
      if (outputContent == null) throw new InvalidDiffRequestException("Can't process file: " + output.getPresentableUrl());
      byte[] originalContent = ReadAction.compute(() -> output.contentsToByteArray());

      List<DiffContent> contents = new ArrayList<>(3);
      List<byte[]> byteContents = new ArrayList<>(3);
      for (VirtualFile file : fileContents) {
        FileContent content = myContentFactory.createFile(project, file);
        if (content == null) throw new InvalidDiffRequestException("Can't process file: " + file.getPresentableUrl());
        contents.add(content);
        byteContents.add(ReadAction.compute(() -> file.contentsToByteArray())); // TODO: we can read contents from file when needed
      }

      BinaryMergeRequestImpl request = new BinaryMergeRequestImpl(project, outputContent, originalContent, contents, byteContents,
                                                                  title, contentTitles);
      return MergeCallback.register(request, applyCallback);
    }
    catch (IOException e) {
      throw new InvalidDiffRequestException("Can't read from file", e);
    }
  }
}
