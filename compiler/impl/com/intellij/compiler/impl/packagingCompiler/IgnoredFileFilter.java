/**
 * @author cdr
 */
package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;

import java.io.File;
import java.io.FileFilter;

public class IgnoredFileFilter implements FileFilter {
    public boolean accept(File file) {
      final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
      final String name = file.getName();
      return !fileTypeManager.isFileIgnored(name)
        && fileTypeManager.getFileTypeByFileName(name) != StdFileTypes.IDEA_PROJECT
        && fileTypeManager.getFileTypeByFileName(name) != StdFileTypes.IDEA_MODULE
        && fileTypeManager.getFileTypeByFileName(name) != StdFileTypes.IDEA_WORKSPACE
        ;
    }
}