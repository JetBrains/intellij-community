/*
 * @author: Eugene Zhuravlev
 * Date: Jan 17, 2003
 * Time: 3:22:59 PM
 */
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.CompilerException;
import com.intellij.compiler.impl.javaCompiler.javac.JavacCompiler;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class AnnotationProcessingCompiler implements SourceProcessingCompiler{
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.JavaCompiler");
  private final Project myProject;

  public AnnotationProcessingCompiler(Project project) {
    myProject = project;
  }

  @NotNull
  public String getDescription() {
    return CompilerBundle.message("annotation.processing.compiler.description");
  }

  @NotNull
  public ProcessingItem[] getProcessingItems(CompileContext context) {
    final VirtualFile[] files = context.getCompileScope().getFiles(StdFileTypes.JAVA, true);
    ProcessingItem[] items = new ProcessingItem[files.length];
    for (int idx = 0; idx < files.length; idx++) {
      items[idx] = new MyProcessingItem(files[idx]);
    }
    return items;
  }

  public ProcessingItem[] process(CompileContext context, ProcessingItem[] items) {
    VirtualFile[] files = new VirtualFile[items.length];
    for (int idx = 0; idx < items.length; idx++) {
      files[idx] = items[idx].getFile();
    }
    compile(context, files);
    return context.getMessageCount(CompilerMessageCategory.ERROR) == 0? items : ProcessingItem.EMPTY_ARRAY;
  }

  public ValidityState createValidityState(DataInput in) throws IOException {
    return null;
  }

  private void compile(final CompileContext context, final VirtualFile[] files) {
    final JavacCompiler javacCompiler = getBackEndCompiler();
    final boolean processorMode = javacCompiler.setAnnotationProcessorMode(true);
    final BackendCompilerWrapper wrapper = new BackendCompilerWrapper(myProject, Arrays.asList(files), (CompileContextEx)context, javacCompiler, DummySink.INSTANCE);
    try {
      wrapper.compile();
    }
    catch (CompilerException e) {
      context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
    }
    catch (CacheCorruptedException e) {
      LOG.info(e);
      context.requestRebuildNextTime(e.getMessage());
    }
    finally {
      javacCompiler.setAnnotationProcessorMode(processorMode);
      final Set<VirtualFile> dirsToRefresh = new HashSet<VirtualFile>();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          final Set<Module> modules = new HashSet<Module>();
          for (VirtualFile file : files) {
            final Module module = context.getModuleByFile(file);
            if (module != null) {
              modules.add(module);
            }
          }
          for (Module module : modules) {
            dirsToRefresh.add(context.getModuleOutputDirectory(module));
            dirsToRefresh.add(context.getModuleOutputDirectoryForTests(module));
            // todo: Some annotation processors put files into the source code. So need to refresh module source roots.
            // It is an open question whether we shall support such processors
            //dirsToRefresh.addAll(Arrays.asList(ModuleRootManager.getInstance(module).getSourceRoots()));
          }
        }
      });
      for (VirtualFile root : dirsToRefresh) {
        root.refresh(false, true);
      }
    }
  }

  public boolean validateConfiguration(CompileScope scope) {
    final JavacCompiler compiler = getBackEndCompiler();
    final boolean previousValue = compiler.setAnnotationProcessorMode(true);
    try {
      return compiler.checkCompiler(scope);
    }
    finally {
      compiler.setAnnotationProcessorMode(previousValue);
    }
  }

  private JavacCompiler getBackEndCompiler() {
    CompilerConfigurationImpl configuration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
    return configuration.getJavacCompiler();
  }

  private static class MyProcessingItem implements ProcessingItem {
    private final VirtualFile myFile;

    public MyProcessingItem(VirtualFile file) {
      myFile = file;
    }

    @NotNull
    public VirtualFile getFile() {
      return myFile;
    }

    public ValidityState getValidityState() {
      return null;
    }
  }

  private static class DummySink implements TranslatingCompiler.OutputSink {
    public static final DummySink INSTANCE = new DummySink();
    public void add(String outputRoot, Collection<TranslatingCompiler.OutputItem> items, VirtualFile[] filesToRecompile) {
    }
  }
}