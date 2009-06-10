/*
 * @author: Eugene Zhuravlev
 * Date: Jan 17, 2003
 * Time: 3:22:59 PM
 */
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.CompilerException;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Collection;

public class JavaCompiler implements TranslatingCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.JavaCompiler");
  private final Project myProject;
  private static final FileTypeManager FILE_TYPE_MANAGER = FileTypeManager.getInstance();

  public JavaCompiler(Project project) {
    myProject = project;
  }

  @NotNull
  public String getDescription() {
    return CompilerBundle.message("java.compiler.description");
  }

  public boolean isCompilableFile(VirtualFile file, CompileContext context) {
    return FILE_TYPE_MANAGER.getFileTypeByFile(file).equals(StdFileTypes.JAVA);
  }

  public ExitStatus compile(CompileContext context, VirtualFile[] files) {
    final BackendCompiler backEndCompiler = getBackEndCompiler();
    final BackendCompilerWrapper wrapper = new BackendCompilerWrapper(myProject, Arrays.asList(files), (CompileContextEx)context, backEndCompiler);
    List<OutputItem> outputItems;
    try {
      outputItems = wrapper.compile();
    }
    catch (CompilerException e) {
      outputItems = Collections.emptyList();
      context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
    }
    catch (CacheCorruptedException e) {
      LOG.info(e);
      context.requestRebuildNextTime(e.getMessage());
      outputItems = Collections.emptyList();
    }

    Collection<VirtualFile> vf = wrapper.getFilesToRecompile();
    return new ExitStatusImpl(outputItems, vf.toArray(new VirtualFile[vf.size()]));
  }

  public boolean validateConfiguration(CompileScope scope) {
    return getBackEndCompiler().checkCompiler(scope);
  }

  private BackendCompiler getBackEndCompiler() {
    CompilerConfigurationImpl configuration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
    return configuration.getDefaultCompiler();
  }

  private static class ExitStatusImpl implements ExitStatus {
    private final List<OutputItem> myOutputItems;
    private final VirtualFile[] myMyFilesToRecompile;

    private ExitStatusImpl(List<OutputItem> outputItems, VirtualFile[] myFilesToRecompile) {
      myOutputItems = outputItems;
      myMyFilesToRecompile = myFilesToRecompile;
    }

    public OutputItem[] getSuccessfullyCompiled() {
      return myOutputItems.toArray(new OutputItem[myOutputItems.size()]);
    }

    public VirtualFile[] getFilesToRecompile() {
      return myMyFilesToRecompile;
    }
  }
}
