/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.application;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;

/**
 * A kind of transaction used in {@link TransactionGuard#submitMergeableTransaction(Disposable, TransactionKind, Runnable)},
 * {@link TransactionGuard#acceptNestedTransactions(TransactionKind...)}.
 */
public interface TransactionKind {
  /**
   * Same as {@link Common#TEXT_EDITING}
   */
  TransactionKind TEXT_EDITING = Common.TEXT_EDITING;

  /**
   * Same as {@link Common#ANY_CHANGE}
   */
  TransactionKind ANY_CHANGE = Common.ANY_CHANGE;

  /**
   * An auxiliary enum to make it possible to use transaction kinds in annotations
   */
  enum Common implements TransactionKind {

    /**
     * This kind represents document modifications via editor actions, code completion and document->PSI commit.
     * @see com.intellij.psi.PsiDocumentManager#commitDocument(Document)
     */
    TEXT_EDITING,

    /**
     * This kind represents any model modifications:
     * <li>PSI or document changes
     * <li>Virtual file system changes, e.g. files created/deleted/renamed/content-changed,
     * caused by refresh process or explicit operations.
     * <li>Project root set change
     * <li>Dumb mode (reindexing) start/finish, (see {@link com.intellij.openapi.project.DumbService}).
     */
    ANY_CHANGE

  }
}
