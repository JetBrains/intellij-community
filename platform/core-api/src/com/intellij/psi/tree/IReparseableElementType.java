// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.psi.tree;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A lazy-parseable element type which allows for incremental reparse. When the infrastructure detects
 * that all the document's changes are inside an AST node with reparseable type,
 * {@link #isReparseable(ASTNode, CharSequence, Language, Project)} is invoked, and if it's successful,
 * only the contents inside this element are reparsed instead of the whole file. This can speed up reparse dramatically.
 */
public class IReparseableElementType extends ILazyParseableElementType implements IReparseableElementTypeBase {
  public IReparseableElementType(@NotNull @NonNls String debugName) {
    super(debugName);
  }

  public IReparseableElementType(@NotNull @NonNls String debugName, @NotNull Language language) {
    super(debugName, language);
  }

  /**
   * Allows to construct element types without registering them, as in {@link IElementType#IElementType(String, Language, boolean)}.
   */
  public IReparseableElementType(@NotNull @NonNls String debugName, @NotNull Language language, boolean register) {
    super(debugName, language, register);
  }


  /**
   * Checks if the specified character sequence can be parsed as a valid content of the
   * chameleon node.
   *
   * @param buffer  the content to parse.
   * @param fileLanguage language of the file
   * @param project the project containing the content.
   * @return true if the content is valid, false if not
   */

  public boolean isParsable(@NotNull CharSequence buffer,
                            @NotNull Language fileLanguage,
                            @NotNull Project project) {
    return false;
  }

  /**
   * The same as {@link this#isParsable(CharSequence, Language, Project)}
   * but with parent ASTNode of the old node.
   *
   * Override this method only if you really understand what are doing.
   * In other cases override {@link this#isParsable(CharSequence, Language, Project)}
   *
   * Known valid use-case:
   *  Indent-based languages. You should know about parent indent in order to decide if block is reparseable with given text.
   *  Because if indent of some line became equals to parent indent then the block should have another parent or block is not block anymore.
   *  So it cannot be reparsed and whole file or parent block should be reparsed.
   *
   * @param parent parent node of old (or collapsed) reparseable node.
   * @param buffer the content to parse.
   * @param fileLanguage language of the file
   * @param project the project containing the content.
   * @return true if the content is valid, false if not
   */
  @Override
  public boolean isParsable(@Nullable ASTNode parent,
                            @NotNull CharSequence buffer,
                            @NotNull Language fileLanguage,
                            @NotNull Project project) {
    return isParsable(buffer, fileLanguage, project);
  }

  @Override
  public boolean isValidReparse(@NotNull ASTNode oldNode, @NotNull ASTNode newNode) {
    return true;
  }

  // Please, add no more public methods here. Add them to `IReparseableElementTypeBase` instead.
  // If you are not sure about the API stability, use `ApiStatus.Experimental` annotation
}
