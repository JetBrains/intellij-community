// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.lang.LighterAST;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class IndexingDataKeys {
  public static final Key<VirtualFile> VIRTUAL_FILE = new Key<>("Context virtual file");
  /**
   * @deprecated use {@link IndexedFileImpl#setProject(Project)} or {@link IndexedFileImpl#getProject()} instead
   */
  @Deprecated
  public static final Key<Project> PROJECT = new Key<>("Context project");
  public static final Key<PsiFile> PSI_FILE = new Key<>("PSI for stubs");
  public static final Key<CharSequence> FILE_TEXT_CONTENT_KEY = Key.create("file text content cached by stub indexer");
  public static final Key<LighterAST> LIGHTER_AST_NODE_KEY = Key.create("lighter.ast.node");
  public static final Key<Boolean> REBUILD_REQUESTED = Key.create("index.rebuild.requested");
}
