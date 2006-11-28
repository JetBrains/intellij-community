/*
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Nov 28, 2006
 * Time: 4:33:11 PM
 */
package com.intellij.psi.codeStyle;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.fileTypes.FileType;

public interface FileTypeIndentOptionsProvider {
  CodeStyleSettings.IndentOptions createIndentOptions();

  FileType getFileType();
}