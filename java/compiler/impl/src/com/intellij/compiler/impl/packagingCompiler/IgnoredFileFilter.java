/*
 * Copyright 2000-2009 JetBrains s.r.o.
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