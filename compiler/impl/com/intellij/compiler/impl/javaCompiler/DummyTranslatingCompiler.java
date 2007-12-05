package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 4, 2007
 */
public class DummyTranslatingCompiler implements TranslatingCompiler, IntermediateOutputCompiler{
  @NonNls private static final String DESCRIPTION = "DUMMY TRANSLATOR";
  @NonNls public static final String FILETYPE_EXTENSION = ".dummy";

  public DummyTranslatingCompiler() {
  }
  
  public static final FileType INPUT_FILE_TYPE = new FileType() {

      @NotNull @NonNls
      public String getName() {
        return "DUMMY";
      }

      @NotNull
      public String getDescription() {
        return "DUMMY_TYPE";
      }

      @NotNull @NonNls
      public String getDefaultExtension() {
        return FILETYPE_EXTENSION;
      }

      @Nullable
      public Icon getIcon() {
        return null;
      }

      public boolean isBinary() {
        return false;
      }

      public boolean isReadOnly() {
        return false;
      }

      @Nullable @NonNls
      public String getCharset(@NotNull final VirtualFile file) {
        return null;
      }
    };

  public boolean isCompilableFile(final VirtualFile file, final CompileContext context) {
    return file.getName().endsWith(FILETYPE_EXTENSION);
  }

  public ExitStatus compile(final CompileContext context, final VirtualFile[] files) {
    final List<OutputItem> items = new ArrayList<OutputItem>();
    final List<File> filesToRefresh = new ArrayList<File>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (final VirtualFile file : files) {
          final Module module = context.getModuleByFile(file);
          try {
            final VirtualFile outputDir = context.getModuleOutputDirectory(module);
            if (outputDir != null) {
              final File compiledFile = doCompile(outputDir, file);
              filesToRefresh.add(compiledFile);
              items.add(new OutputItemImpl(outputDir.getPath(), FileUtil.toSystemIndependentName(compiledFile.getPath()), file));
            }
          }
          catch (IOException e) {
            context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, 0, 0);
          }
        }
      }
    });
    CompilerUtil.refreshIOFiles(filesToRefresh);
    return new ExitStatus() {
      public OutputItem[] getSuccessfullyCompiled() {
        return items.toArray(new OutputItem[items.size()]);
      }

      public VirtualFile[] getFilesToRecompile() {
        return VirtualFile.EMPTY_ARRAY;
      }
    };
  }

  private File doCompile(VirtualFile out, VirtualFile src) throws IOException {
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
