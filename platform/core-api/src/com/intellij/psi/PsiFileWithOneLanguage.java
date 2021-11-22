// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

/**
 * Represents a file that contains elements with only one language.
 * It simplifies language detection in the editor caret position: procedure doesn't need to re-parse lazy elements.
 * For example, an HTML file is not a mono-language file: it may contain HTML and CSS elements in one PSI tree.
 * On the other hand, C/C++ files have a mono-language implementation.
 * (although C/C++ file may contain injections of other languages, but these do not interfere with the main file PSI tree).
 */
public interface PsiFileWithOneLanguage extends PsiFile {
}
