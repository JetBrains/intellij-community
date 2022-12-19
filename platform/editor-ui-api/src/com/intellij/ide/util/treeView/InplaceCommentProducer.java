// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView;

import org.jetbrains.annotations.NotNull;

/**
 * The interface to produce inplace comments for a tree node..
 * <p>
 *   For example, timestamps and sizes in the Project View.
 *   Implementations of this interface contain node-specific logic
 *   that generates actual comments.
 * </p>
 */
public interface InplaceCommentProducer {

  /**
   * Generates inplace comments and appends it to the given appender.
   * @param appender the appender to append comments to
   */
  void produceInplaceComments(@NotNull InplaceCommentAppender appender);

}
