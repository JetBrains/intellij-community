// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.templates.github;

import com.intellij.lang.LangBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.io.Decompressor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public final class ZipUtil {
  private static final Logger LOG = Logger.getInstance(ZipUtil.class);

  public interface ContentProcessor {
    /** Return null to skip the file */
    byte @Nullable [] processContent(byte[] content, File file) throws IOException;
  }

  public static void unzipWithProgressSynchronously(@Nullable Project project,
                                                    @NotNull @NlsContexts.ProgressTitle String progressTitle,
                                                    @NotNull Path zipArchive,
                                                    @NotNull Path extractToDir,
                                                    boolean unwrapSingleTopLevelFolder) throws GeneratorException {
    unzipWithProgressSynchronously(project, progressTitle, zipArchive.toFile(), extractToDir.toFile(), null, unwrapSingleTopLevelFolder);
  }

  public static void unzipWithProgressSynchronously(
    @Nullable Project project,
    @NotNull @NlsContexts.ProgressTitle String progressTitle,
    final @NotNull File zipArchive,
    final @NotNull File extractToDir,
    final @Nullable NullableFunction<? super String, String> pathConvertor,
    final boolean unwrapSingleTopLevelFolder) throws GeneratorException
  {
    final Outcome<Boolean> outcome = DownloadUtil.provideDataWithProgressSynchronously(
      project, progressTitle, LangBundle.message("progress.text.unpacking"),
      () -> {
        ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        unzip(progress, extractToDir, zipArchive, pathConvertor, null, unwrapSingleTopLevelFolder);
        return true;
      },
      () -> false
    );
    Boolean result = outcome.get();
    if (result == null) {
      Exception e = outcome.getException();
      if (e != null) {
        throw new GeneratorException(LangBundle.message("dialog.message.unpacking.failed.downloaded.archive.broken"));
      }
      throw new GeneratorException(LangBundle.message("dialog.message.unpacking.was.cancelled"));
    }
  }

  private static @NotNull Path getUnzipToDir(@Nullable ProgressIndicator progress,
                                             @NotNull Path targetDir,
                                             boolean unwrapSingleTopLevelFolder) throws IOException {
    if (progress != null) {
      progress.setText(LangBundle.message("progress.text.extracting"));
    }
    return unwrapSingleTopLevelFolder ? Files.createTempDirectory("unzip-dir-") : targetDir;
  }

  // This method will throw IOException, if a zipArchive file isn't a valid zip archive.
  public static void unzip(@Nullable ProgressIndicator progress,
                           @NotNull File targetDir,
                           @NotNull File zipArchive,
                           @Nullable NullableFunction<? super String, String> pathConvertor,
                           @Nullable ContentProcessor contentProcessor,
                           boolean unwrapSingleTopLevelFolder) throws IOException {
    Path unzipToDir = getUnzipToDir(progress, targetDir.toPath(), unwrapSingleTopLevelFolder);
    try (ZipFile zipFile = new ZipFile(zipArchive, ZipFile.OPEN_READ)) {
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        try (InputStream entryContentStream = zipFile.getInputStream(entry)) {
          unzipEntryToDir(progress, entry, entryContentStream, unzipToDir, pathConvertor, contentProcessor);
        }
      }
    }
    if (unwrapSingleTopLevelFolder) {
      doUnwrapSingleTopLevelFolder(unzipToDir, targetDir.toPath());
    }
  }

  public static void unzip(@Nullable ProgressIndicator progress,
                           @NotNull Path targetDir,
                           @NotNull ZipInputStream stream,
                           @Nullable NullableFunction<? super String, String> pathConvertor,
                           @Nullable ContentProcessor contentProcessor,
                           boolean unwrapSingleTopLevelFolder) throws IOException {
    Path unzipToDir = getUnzipToDir(progress, targetDir, unwrapSingleTopLevelFolder);
    ZipEntry entry;
    while ((entry = stream.getNextEntry()) != null) {
      unzipEntryToDir(progress, entry, stream, unzipToDir,  pathConvertor, contentProcessor);
    }
    if (unwrapSingleTopLevelFolder) {
      doUnwrapSingleTopLevelFolder(unzipToDir, targetDir);
    }
  }

  private static void doUnwrapSingleTopLevelFolder(@NotNull Path unzipToDir, @NotNull Path targetDir) throws IOException {
    List<Path> topLevelFiles;
    try (Stream<Path> stream = Files.list(unzipToDir)) {
      topLevelFiles = stream.collect(Collectors.toList());
    }
    catch (NoSuchFileException e) {
      return;
    }

    Path dirToMove;
    List<Path> children;
    if (topLevelFiles.size() == 1 && Files.isDirectory(topLevelFiles.get(0))) {
      dirToMove = topLevelFiles.get(0);
      try (Stream<Path> stream = Files.list(dirToMove)) {
        children = stream.collect(Collectors.toList());
      }
    }
    else {
      dirToMove = unzipToDir;
      children = topLevelFiles;
    }

    // don't "FileUtil.moveDirWithContent(dirToMove, targetDir)"
    // because a file moved with "java.io.File.renameTo" won't inherit its new parent's permissions
    for (Path child : children) {
      Path to = targetDir.resolve(dirToMove.relativize(child));
      if (Files.isDirectory(child)) {
        FileUtil.copyDir(child.toFile(), to.toFile(), true);
      }
      else {
        Files.createDirectories(to.getParent());
        Files.copy(child, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
      }
    }
    FileUtil.delete(unzipToDir);
  }

  private static void unzipEntryToDir(@Nullable ProgressIndicator progress,
                                      final @NotNull ZipEntry zipEntry,
                                      final @NotNull InputStream entryContentStream,
                                      @NotNull Path extractToDir,
                                      @Nullable NullableFunction<? super String, String> pathConvertor,
                                      @Nullable ContentProcessor contentProcessor) throws IOException {
    String relativeExtractPath = createRelativeExtractPath(zipEntry);
    if (pathConvertor != null) {
      relativeExtractPath = pathConvertor.fun(relativeExtractPath);
      if (relativeExtractPath == null) {
        // should be skipped
        return;
      }
    }
    Path child = Decompressor.entryFile(extractToDir, relativeExtractPath);
    Path dir = zipEntry.isDirectory() ? child : child.getParent();
    Files.createDirectories(dir);
    LOG.assertTrue(dir.toFile().exists());
    LOG.assertTrue(dir.toFile().listFiles() != null);
    if (zipEntry.isDirectory()) {
      return;
    }
    if (progress != null) {
      progress.setText2(LangBundle.message("progress.text.extracting.path", relativeExtractPath));
    }
    if (contentProcessor == null) {
      Files.copy(entryContentStream, child, StandardCopyOption.REPLACE_EXISTING);
    }
    else {
      byte[] content = contentProcessor.processContent(FileUtilRt.loadBytes(entryContentStream), child.toFile());
      if (content != null) {
        Files.write(child, content);
      }
    }
    LOG.info("Extract: " + relativeExtractPath);
  }

  private static @NotNull String createRelativeExtractPath(@NotNull ZipEntry zipEntry) {
    String name = StringUtil.trimStart(zipEntry.getName(), "/");
    return StringUtil.trimEnd(name, "/");
  }
}
