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
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Chunk;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 4, 2007
 */
public class DummyTranslatingCompiler implements TranslatingCompiler, IntermediateOutputCompiler{
  @NonNls private static final String DESCRIPTION = "DUMMY TRANSLATOR";
  @NonNls private static final String FILETYPE_EXTENSION = ".dummy";

  public boolean isCompilableFile(final VirtualFile file, final CompileContext context) {
    return file.getName().endsWith(FILETYPE_EXTENSION);
  }

  public void compile(final CompileContext context, Chunk<Module> moduleChunk, final VirtualFile[] files, OutputSink sink) {
    final List<File> filesToRefresh = new ArrayList<File>();
    final Map<String, Collection<OutputItem>> outputs = new HashMap<String, Collection<OutputItem>>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (final VirtualFile file : files) {
          final Module module = context.getModuleByFile(file);
          try {
            final VirtualFile outputDir = context.getModuleOutputDirectory(module);
            if (outputDir != null) {
              final String outputDirPath = outputDir.getPath();
              final File compiledFile = doCompile(outputDir, file);
              filesToRefresh.add(compiledFile);
              Collection<OutputItem> collection = outputs.get(outputDirPath);
              if (collection == null) {
                collection = new ArrayList<OutputItem>();
                outputs.put(outputDirPath, collection);
              }
              collection.add(new OutputItemImpl(FileUtil.toSystemIndependentName(compiledFile.getPath()), file));
            }
          }
          catch (IOException e) {
            context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, 0, 0);
          }
        }
      }
    });
    CompilerUtil.refreshIOFiles(filesToRefresh);
    for (Map.Entry<String, Collection<OutputItem>> entry : outputs.entrySet()) {
      sink.add(entry.getKey(), entry.getValue(), VirtualFile.EMPTY_ARRAY);
    }
  }

  private static File doCompile(VirtualFile out, VirtualFile src) throws IOException {
    final String originalName = src.getName();
    String compiledName = originalName.substring(0, originalName.length() - FILETYPE_EXTENSION.length());
    final File destFile = new File(out.getPath(), compiledName + ".java");
    FileUtil.copy(new File(src.getPath()), destFile);
    return destFile;
  }
  
  @NotNull
  public String getDescription() {
    return DESCRIPTION;
  }

  public boolean validateConfiguration(final CompileScope scope) {
    return true;
  }
}
