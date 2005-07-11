/*
 * Copyright (c) 2000-05 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */

package com.intellij.lang;

import org.jetbrains.annotations.Nullable;

/**
 * Defines the support for "Comment with Line Comment" and "Comment with Block Comment"
 * actions in a custom language.
 * @author max
 * @see com.intellij.lang.Language#getCommenter()
 */
public interface Commenter {
  /**
   * Returns the string which prefixes a line comment in the language, or null if the language
   * does not support line comments.
   * @return the line comment text, or null.
   */
  @Nullable
  String getLineCommentPrefix();

  /**
   * Returns true if the line comments in the language must start at the zero column and may not
   * be indented.
   * @return true if line comments may not be indented, false otherwise.
   */
  boolean isLineCommentPrefixOnZeroColumn();

  /**
   * Returns the string which marks the beginning of a block comment in the language,
   * or null if the language does not support block comments.
   * @return the block comment start text, or null.
   */
  @Nullable
  String getBlockCommentPrefix();

  /**
   * Returns the string which marks the end of a block comment in the language,
   * or null if the language does not support block comments.
   * @return the block comment end text, or null.
   */
  @Nullable
  String getBlockCommentSuffix();
}
