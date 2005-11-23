/*
 * @author: Eugene Zhuravlev
 * Date: Jan 17, 2003
 * Time: 3:22:59 PM
 */
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerException;
import com.intellij.compiler.JavacSettings;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
                                                     
public class JavaCompiler implements TranslatingCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.JavaCompiler");
  private Project myProject;
  private BackendCompiler JAVAC_EXTERNAL_BACKEND;
  private BackendCompiler JAVAC_EMBEDDED_BACKEND;
  private BackendCompiler JIKES_BACKEND;
  private BackendCompiler ECLIPSE_BACKEND;
  @NonNls private static final String PROPERTY_IDEA_USE_EMBEDDED_JAVAC = "idea.use.embedded.javac";

  public JavaCompiler(Project project) {
    myProject = project;
    JAVAC_EXTERNAL_BACKEND = new JavacCompiler(project);
    JAVAC_EMBEDDED_BACKEND = new JavacEmbeddedCompiler(project);
    JIKES_BACKEND = new JikesCompiler(project);
    ECLIPSE_BACKEND = new EclipseCompiler(project);
  }

  @NotNull
  public String getDescription() {
    return CompilerBundle.message("java.compiler.description");
  }

  public boolean isCompilableFile(VirtualFile file, CompileContext context) {
    return FileTypeManager.getInstance().getFileTypeByFile(file).equals(StdFileTypes.JAVA);
  }

  public ExitStatus compile(CompileContext context, VirtualFile[] files) {
    final BackendCompiler backEndCompiler = getBackEndCompiler();
    final BackendCompilerWrapper wrapper = new BackendCompilerWrapper(myProject, files, (CompileContextEx)context, backEndCompiler);
    TranslatingCompiler.OutputItem[] outputItems;
    try {
      outputItems = wrapper.compile();
    }
    catch (CompilerException e) {
      outputItems = TranslatingCompiler.EMPTY_OUTPUT_ITEM_ARRAY;
      context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
    }
    catch (CacheCorruptedException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
      context.requestRebuildNextTime(e.getMessage());
      outputItems = TranslatingCompiler.EMPTY_OUTPUT_ITEM_ARRAY;
    }

    return new ExitStatusImpl(outputItems, wrapper.getFilesToRecompile());
  }

  public boolean validateConfiguration(CompileScope scope) {
    return getBackEndCompiler().checkCompiler();
  }

  private BackendCompiler getBackEndCompiler() {
    //if (true) return ECLIPSE_BACKEND;
    CompilerConfiguration configuration = CompilerConfiguration.getInstance(myProject);
    if (CompilerConfiguration.JIKES.equals(configuration.getDefaultCompiler())) {
      return JIKES_BACKEND;
    }
    else {
      boolean runEmbedded = ApplicationManager.getApplication().isUnitTestMode()
                            ? !JavacSettings.getInstance(myProject).isTestsUseExternalCompiler()
                            : Boolean.parseBoolean(System.getProperty(PROPERTY_IDEA_USE_EMBEDDED_JAVAC));

      return runEmbedded ? JAVAC_EMBEDDED_BACKEND : JAVAC_EXTERNAL_BACKEND;
    }
  }

  private static class ExitStatusImpl implements ExitStatus {

    private OutputItem[] myOuitputItems;
    private VirtualFile[] myMyFilesToRecompile;

    public ExitStatusImpl(TranslatingCompiler.OutputItem[] ouitputItems, VirtualFile[] myFilesToRecompile) {
      myOuitputItems = ouitputItems;
      myMyFilesToRecompile = myFilesToRecompile;
    }

    public TranslatingCompiler.OutputItem[] getSuccessfullyCompiled() {
      return myOuitputItems;
    }

    public VirtualFile[] getFilesToRecompile() {
      return myMyFilesToRecompile;
    }
  }
}
