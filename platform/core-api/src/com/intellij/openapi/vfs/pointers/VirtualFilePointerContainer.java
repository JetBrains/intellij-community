// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.pointers;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Modifiable and persistable set of {@link VirtualFilePointer}s.
 *
 * @see VirtualFilePointerManager#createContainer
 */
public interface VirtualFilePointerContainer {
  void killAll();

  void add(@NotNull VirtualFile file);

  void add(@NotNull String url);

  void remove(@NotNull VirtualFilePointer pointer);

  @NotNull
  @Unmodifiable List<VirtualFilePointer> getList();

  void addAll(@NotNull VirtualFilePointerContainer that);

  String @NotNull [] getUrls();

  boolean isEmpty();

  VirtualFile @NotNull [] getFiles();

  VirtualFile @NotNull [] getDirectories();

  @Nullable
  VirtualFilePointer findByUrl(@NotNull String url);

  void clear();

  int size();

  /**
   * For example, to read from the xml below, call {@code readExternal(myRootTag, "childElementName"); }
   * <pre>{@code
   * <myroot>
   *   <childElementName url="xxx1"/>
   *   <childElementName url="xxx2"/>
   * </myroot>
   * }</pre>
   */
  void readExternal(@NotNull Element rootChild, @NotNull String childElementName, boolean externalizeJarDirectories)
    throws InvalidDataException;

  void writeExternal(@NotNull Element element, @NotNull String childElementName, boolean externalizeJarDirectories);

  void moveUp(@NotNull String url);

  void moveDown(@NotNull String url);

  @NotNull
  VirtualFilePointerContainer clone(@NotNull Disposable parent);

  @NotNull
  VirtualFilePointerContainer clone(@NotNull Disposable parent, @Nullable VirtualFilePointerListener listener);

  /**
   * Adds {@code directory} as a root of jar files.
   * After this call the {@link #getFiles()} will additionally return jar files in this directory
   * (and, if {@code recursively} was set, the jar files in all-subdirectories).
   * {@link #getUrls()} will additionally return the {@code directoryUrl}.
   */
  void addJarDirectory(@NotNull String directoryUrl, boolean recursively);

  /**
   * Removes {@code directory} from the roots of jar files.
   * After that the {@link #getFiles()} and {@link #getUrls()} etc will not return jar files in this directory anymore.
   *
   * @return true if removed
   */
  boolean removeJarDirectory(@NotNull String directoryUrl);

  /**
   * Returns list of (directory url, isRecursive) which were added via {@link #addJarDirectory(String, boolean)} }
   */
  @NotNull
  @Unmodifiable
  List<Pair<String, Boolean>> getJarDirectories();
}
