// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex.util;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * An experiment API for providing custom way of storing lexer-based highlighting data.
 * <p>
 * By default a highlighting lexer uses {@link ShortBasedStorage} implementation which
 * serializes information about element type to be highlighted with their indices ({@link IElementType#getIndex()})
 * and deserializes ids back to {@link IElementType} using
 * element types registry {@link IElementType#find(short)}.
 * <p>
 * If you need to store more information during syntax highlighting or
 * if your element types cannot be restored from {@link IElementType#getIndex()},
 * you can implement you own storage and make your highlighting lexer implement {@link DataStorageFactory}
 * that will create the custom storage.
 * <p>
 * As an example, see {@link org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateHighlightingLexer},
 * that lexes files with unregistered (whitout index) element types and
 * its data storage ({@link org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateLexerDataStorage}
 * serializes/deserializes them to/from strings.
 *
 * @see com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
 * @see SegmentArrayWithData
 */
@ApiStatus.Experimental
public interface DataStorageFactory {
  @NotNull
  DataStorage createDataStorage();
}
