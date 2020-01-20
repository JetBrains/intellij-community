// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use {@link DocumentEventImpl#getMoveOffset()} among with a {@link DocumentEventImpl#isPreMoveInsertion()} check.
 */
@Deprecated
public interface PrioritizedInternalDocumentListener extends PrioritizedDocumentListener {
  void moveTextHappened(@NotNull Document document, int start, int end, int base);
}
