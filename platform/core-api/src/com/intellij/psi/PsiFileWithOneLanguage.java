// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

/**
 * Represents a file that contains elements with only one language.
 * That simplifies language detection in editor position: procedure doesn't need re-parse lazy elements.
 * For example HTML file is not a mono-language file - it may contain HTML and CSS elements in one psi-tree,
 * but C/C++ file has mono-language implementation, although C/C++ file may contain the injections of other languages.
 */
public interface PsiFileWithOneLanguage extends PsiFile {
}
