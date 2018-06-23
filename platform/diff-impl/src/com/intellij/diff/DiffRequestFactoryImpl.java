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
package com.intellij.diff;

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.merge.MergeResult;
import com.intellij.diff.merge.TextMergeRequest;
import com.intellij.diff.requests.BinaryMergeRequestImpl;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.requests.TextMergeRequestImpl;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.util.ObjectUtils.chooseNotNull;

public class DiffRequestFactoryImpl extends DiffRequestFactory {
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
    return getContentTitle(VcsUtil.getFilePath(file));
  }

  @NotNull
  @Override
  public String getTitle(@Nullable VirtualFile file1, @Nullable VirtualFile file2) {
    FilePath path1 = file1 != null ? VcsUtil.getFilePath(file1) : null;
    FilePath path2 = file2 != null ? VcsUtil.getFilePath(file2) : null;
    return getTitle(path1, path2, " vs ");
  }

  @NotNull
  @Override
  public String getTitle(@NotNull VirtualFile file) {
    return getTitle(file, null);
  }

  @NotNull
  public static String getContentTitle(@NotNull FilePath path) {
    if (path.isDirectory()) return path.getPresentableUrl();
    FilePath parent = path.getParentPath();
    return getContentTitle(path.getName(), path.getPresentableUrl(), parent != null ? parent.getPresentableUrl() : null);
  }

  @NotNull
  public static String getTitle(@Nullable FilePath path1, @Nullable FilePath path2, @NotNull String separator) {
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

  @NotNull
  private static String getContentTitle(@NotNull String name, @NotNull String path, @Nullable String parentPath) {
    if (parentPath != null) {
      return name + " (" + parentPath + ")";
    }
    else {
      return path;
    }
  }

  @NotNull
  private static String getRequestTitle(@NotNull String name1, @NotNull String path1, @Nullable String parentPath1,
                                        @NotNull String name2, @NotNull String path2, @Nullable String parentPath2,
                                        @NotNull String sep) {
    if (path1.equals(path2)) return getContentTitle(name1, path1, parentPath1);

    if (Comparing.equal(parentPath1, parentPath2)) {
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
                                         @Nullable String title,
                                         @NotNull List<String> titles,
                                         @Nullable Consumer<MergeResult> applyCallback) throws InvalidDiffRequestException {
    if (textContents.size() != 3) throw new IllegalArgumentException();
    if (titles.size() != 3) throw new IllegalArgumentException();

    if (!DiffUtil.canMakeWritable(outputDocument)) throw new InvalidDiffRequestException("Output is read only");

    DocumentContent outputContent = myContentFactory.create(project, outputDocument, fileType);
    CharSequence originalContent = outputDocument.getImmutableCharSequence();

    List<DocumentContent> contents = new ArrayList<>(3);
    for (String text : textContents) {
      contents.add(myContentFactory.create(project, text, fileType));
    }

    return new TextMergeRequestImpl(project, outputContent, originalContent, contents, title, titles, applyCallback);
  }

  @NotNull
  @Override
  public MergeRequest createMergeRequest(@Nullable Project project,
                                         @NotNull VirtualFile output,
                                         @NotNull List<byte[]> byteContents,
                                         @Nullable String title,
                                         @NotNull List<String> contentTitles,
                                         @Nullable Consumer<MergeResult> applyCallback) throws InvalidDiffRequestException {
    if (byteContents.size() != 3) throw new IllegalArgumentException();
    if (contentTitles.size() != 3) throw new IllegalArgumentException();

    try {
      return createTextMergeRequest(project, output, byteContents, title, contentTitles, applyCallback);
    }
    catch (InvalidDiffRequestException e) {
      return createBinaryMergeRequest(project, output, byteContents, title, contentTitles, applyCallback);
    }
  }

  @NotNull
  @Override
  public TextMergeRequest createTextMergeRequest(@Nullable Project project,
                                                 @NotNull VirtualFile output,
                                                 @NotNull List<byte[]> byteContents,
                                                 @Nullable String title,
                                                 @NotNull List<String> contentTitles,
                                                 @Nullable Consumer<MergeResult> applyCallback) throws InvalidDiffRequestException {
    if (byteContents.size() != 3) throw new IllegalArgumentException();
    if (contentTitles.size() != 3) throw new IllegalArgumentException();

    final Document outputDocument = FileDocumentManager.getInstance().getDocument(output);
    if (outputDocument == null) throw new InvalidDiffRequestException("Can't get output document: " + output.getPresentableUrl());
    if (!DiffUtil.canMakeWritable(outputDocument)) throw new InvalidDiffRequestException("Output is read only: " + output.getPresentableUrl());

    DocumentContent outputContent = myContentFactory.create(project, outputDocument);
    CharSequence originalContent = outputDocument.getImmutableCharSequence();

    List<DocumentContent> contents = new ArrayList<>(3);
    for (byte[] bytes : byteContents) {
      contents.add(myContentFactory.createDocumentFromBytes(project, bytes, output));
    }

    return new TextMergeRequestImpl(project, outputContent, originalContent, contents, title, contentTitles, applyCallback);
  }

  @NotNull
  @Override
  public MergeRequest createBinaryMergeRequest(@Nullable Project project,
                                               @NotNull VirtualFile output,
                                               @NotNull List<byte[]> byteContents,
                                               @Nullable String title,
                                               @NotNull List<String> contentTitles,
                                               @Nullable Consumer<MergeResult> applyCallback) throws InvalidDiffRequestException {
    if (byteContents.size() != 3) throw new IllegalArgumentException();
    if (contentTitles.size() != 3) throw new IllegalArgumentException();

    try {
      FileContent outputContent = myContentFactory.createFile(project, output);
      if (outputContent == null) throw new InvalidDiffRequestException("Can't process file: " + output);
      byte[] originalContent = output.contentsToByteArray();

      List<DiffContent> contents = new ArrayList<>(3);
      for (byte[] bytes : byteContents) {
        contents.add(myContentFactory.createFromBytes(project, bytes, output));
      }

      return new BinaryMergeRequestImpl(project, outputContent, originalContent, contents, byteContents, title, contentTitles, applyCallback);
    }
    catch (IOException e) {
      throw new InvalidDiffRequestException("Can't read from file", e);
    }
  }

  @NotNull
  @Override
  public MergeRequest createMergeRequestFromFiles(@Nullable Project project,
                                                  @NotNull VirtualFile output,
                                                  @NotNull List<VirtualFile> fileContents,
                                                  @Nullable Consumer<MergeResult> applyCallback) throws InvalidDiffRequestException {
    String title = "Merge " + output.getPresentableUrl();
    List<String> titles = ContainerUtil.list("Your Version", "Base Version", "Their Version");
    return createMergeRequestFromFiles(project, output, fileContents, title, titles, applyCallback);
  }

  @NotNull
  @Override
  public MergeRequest createMergeRequestFromFiles(@Nullable Project project,
                                                  @NotNull VirtualFile output,
                                                  @NotNull List<VirtualFile> fileContents,
                                                  @Nullable String title,
                                                  @NotNull List<String> contentTitles,
                                                  @Nullable Consumer<MergeResult> applyCallback) throws InvalidDiffRequestException {
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
                                                          @NotNull List<VirtualFile> fileContents,
                                                          @Nullable String title,
                                                          @NotNull List<String> contentTitles,
                                                          @Nullable Consumer<MergeResult> applyCallback) throws InvalidDiffRequestException {
    List<byte[]> byteContents = new ArrayList<>(3);
    for (VirtualFile file : fileContents) {
      try {
        byteContents.add(file.contentsToByteArray());
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
                                                        @NotNull List<VirtualFile> fileContents,
                                                        @Nullable String title,
                                                        @NotNull List<String> contentTitles,
                                                        @Nullable Consumer<MergeResult> applyCallback) throws InvalidDiffRequestException {
    if (fileContents.size() != 3) throw new IllegalArgumentException();
    if (contentTitles.size() != 3) throw new IllegalArgumentException();


    try {
      FileContent outputContent = myContentFactory.createFile(project, output);
      if (outputContent == null) throw new InvalidDiffRequestException("Can't process file: " + output.getPresentableUrl());
      byte[] originalContent = output.contentsToByteArray();

      List<DiffContent> contents = new ArrayList<>(3);
      List<byte[]> byteContents = new ArrayList<>(3);
      for (VirtualFile file : fileContents) {
        FileContent content = myContentFactory.createFile(project, file);
        if (content == null) throw new InvalidDiffRequestException("Can't process file: " + file.getPresentableUrl());
        contents.add(content);
        byteContents.add(file.contentsToByteArray()); // TODO: we can read contents from file when needed
      }

      return new BinaryMergeRequestImpl(project, outputContent, originalContent, contents, byteContents, title, contentTitles, applyCallback);
    }
    catch (IOException e) {
      throw new InvalidDiffRequestException("Can't read from file", e);
    }
  }
}
