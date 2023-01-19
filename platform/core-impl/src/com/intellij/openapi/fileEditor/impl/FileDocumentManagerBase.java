// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.FrozenDocument;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.psi.FileViewProvider;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

public abstract class FileDocumentManagerBase extends FileDocumentManager {
  public static final Key<Document> HARD_REF_TO_DOCUMENT_KEY = Key.create("HARD_REF_TO_DOCUMENT_KEY");
  public static final Key<Boolean> TRACK_NON_PHYSICAL = Key.create("TRACK_NON_PHYSICAL");

  private static final Key<VirtualFile> FILE_KEY = Key.create("FILE_KEY");
  private static final Key<Boolean> BIG_FILE_PREVIEW = Key.create("BIG_FILE_PREVIEW");
  private static final Object lock = new Object();

  @Override
  public @Nullable Document getDocument(@NotNull VirtualFile file) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    DocumentEx document = (DocumentEx)getCachedDocument(file);
    if (document == null) {
      if (!file.isValid() || file.isDirectory() || isBinaryWithoutDecompiler(file)) return null;

      boolean tooLarge = FileUtilRt.isTooLarge(file.getLength());
      if (file.getFileType().isBinary() && tooLarge) return null;

      CharSequence text = loadText(file, tooLarge);
      synchronized (lock) {
        document = (DocumentEx)getCachedDocument(file);
        if (document != null) return document;  // double-checking

        document = (DocumentEx)createDocument(text, file);
        document.setModificationStamp(file.getModificationStamp());
        setDocumentTooLarge(document, tooLarge);
        FileType fileType = file.getFileType();
        document.setReadOnly(tooLarge || !file.isWritable() || fileType.isBinary());

        if (!(file instanceof LightVirtualFile || file.getFileSystem() instanceof NonPhysicalFileSystem) || Boolean.TRUE.equals(TRACK_NON_PHYSICAL.get(file))) {
          document.addDocumentListener(getDocumentListener());
        }

        if (file instanceof LightVirtualFile) {
          registerDocument(document, file);
        }
        else {
          document.putUserData(FILE_KEY, file);
          cacheDocument(file, document);
        }
      }

      fileContentLoaded(file, document);
    }

    return document;
  }

  protected static void setDocumentTooLarge(Document document, boolean tooLarge) {
    document.putUserData(BIG_FILE_PREVIEW, tooLarge ? Boolean.TRUE : null);
  }

  private @NotNull CharSequence loadText(@NotNull VirtualFile file, boolean tooLarge) {
    if (file instanceof LightVirtualFile) {
      FileViewProvider vp = findCachedPsiInAnyProject(file);
      if (vp != null) {
        return vp.getPsi(vp.getBaseLanguage()).getText();
      }
    }

    return tooLarge ? LoadTextUtil.loadText(file, getPreviewCharCount(file)) : LoadTextUtil.loadText(file);
  }

  protected abstract @NotNull Document createDocument(@NotNull CharSequence text, @NotNull VirtualFile file);

  @Override
  public @Nullable Document getCachedDocument(@NotNull VirtualFile file) {
    Document hard = file.getUserData(HARD_REF_TO_DOCUMENT_KEY);
    return hard != null ? hard : getDocumentFromCache(file);
  }

  /**
   * Storing file<->document association with hard references to avoid undesired GCs.
   * Works for non-physical ViewProviders only, to avoid memory leaks.
   * Please do not use under the penalty of severe memory leaks and wild PSI inconsistencies.
   */
  @ApiStatus.Internal
  public static void registerDocument(@NotNull Document document, @NotNull VirtualFile virtualFile) {
    if (!(virtualFile instanceof LightVirtualFile) &&
        !(virtualFile.getFileSystem() instanceof NonPhysicalFileSystem)) {
      throw new IllegalArgumentException(
        "Hard-coding file<->document association is permitted for non-physical files only (see FileViewProvider.isPhysical())" +
        " to avoid memory leaks. virtualFile=" + virtualFile);
    }
    synchronized (lock) {
      document.putUserData(FILE_KEY, virtualFile);
      virtualFile.putUserData(HARD_REF_TO_DOCUMENT_KEY, document);
    }
  }

  @Override
  public @Nullable VirtualFile getFile(@NotNull Document document) {
    return document instanceof FrozenDocument ? null : document.getUserData(FILE_KEY);
  }

  @Override
  public void reloadBinaryFiles() {
    List<VirtualFile> binaries = ContainerUtil.filter(myDocumentCache.keySet(), file -> file.getFileType().isBinary());
    FileContentUtilCore.reparseFiles(binaries);
  }

  @Override
  public boolean isPartialPreviewOfALargeFile(@NotNull Document document) {
    return document.getUserData(BIG_FILE_PREVIEW) == Boolean.TRUE;
  }

  void unbindFileFromDocument(@NotNull VirtualFile file, @NotNull Document document) {
    removeDocumentFromCache(file);
    file.putUserData(HARD_REF_TO_DOCUMENT_KEY, null);
    document.putUserData(FILE_KEY, null);
  }

  protected static boolean isBinaryWithoutDecompiler(@NotNull VirtualFile file) {
    FileType type = file.getFileType();
    return type.isBinary() && BinaryFileTypeDecompilers.getInstance().forFileType(type) == null;
  }

  protected static int getPreviewCharCount(@NotNull VirtualFile file) {
    Charset charset = EncodingManager.getInstance().getEncoding(file, false);
    float bytesPerChar = charset == null ? 2 : charset.newEncoder().averageBytesPerChar();
    return (int)(FileUtilRt.LARGE_FILE_PREVIEW_SIZE / bytesPerChar);
  }

  private final Map<VirtualFile, Document> myDocumentCache = ContainerUtil.createConcurrentWeakValueMap();

  private void cacheDocument(@NotNull VirtualFile file, @NotNull Document document) {
    myDocumentCache.put(file, document);
  }

  private void removeDocumentFromCache(@NotNull VirtualFile file) {
    myDocumentCache.remove(file);
  }

  private Document getDocumentFromCache(@NotNull VirtualFile file) {
    return myDocumentCache.get(file);
  }

  @ApiStatus.Internal
  protected void clearDocumentCache() {
    myDocumentCache.clear();
  }

  protected abstract void fileContentLoaded(@NotNull VirtualFile file, @NotNull Document document);

  protected abstract @NotNull DocumentListener getDocumentListener();
}
