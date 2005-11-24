package com.intellij.compiler.impl.javaCompiler.eclipse;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.compiler.impl.javaCompiler.CompilerParsingThread;
import com.intellij.compiler.impl.javaCompiler.ModuleChunk;
import com.intellij.compiler.impl.javaCompiler.javac.JavacEmbeddedCompiler;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class EclipseEmbeddedCompiler implements BackendCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.eclipse.EclipseEmbeddedCompiler");

  private Project myProject;
  private final EclipseCompiler myEclipseExternalCompiler;
  private int myExitCode;

  public EclipseEmbeddedCompiler(Project project) {
    myProject = project;
    myEclipseExternalCompiler = new EclipseCompiler(project);
  }

  public boolean checkCompiler() {
    return myEclipseExternalCompiler.checkCompiler();
  }

  @NotNull
  @NonNls
  public String getId() // used for externalization
  {
    return "ECLIPSE_EMBEDDED";
  }

  @NotNull
  public String getPresentableName() {
    return null;
  }

  @NotNull
  public Configurable createConfigurable() {
    return null;
  }

  public OutputParser createErrorParser(final String outputDir) {
    return myEclipseExternalCompiler.createErrorParser(outputDir);
  }

  @Nullable
  public OutputParser createOutputParser(final String outputDir) {
    return myEclipseExternalCompiler.createOutputParser(outputDir);
  }

  public void compileFinished() {
    int i = 0;
  }

  @NotNull
  public Process launchProcess(final ModuleChunk chunk, final String outputDir, final CompileContext compileContext) throws IOException {
    final PipedInputStream errIn = new PipedInputStream();
    PipedOutputStream errOut = new PipedOutputStream(errIn);
    final PrintWriter errWriter = new PrintWriter(errOut);

    final PipedInputStream outIn = new PipedInputStream();
    PipedOutputStream outOut = new PipedOutputStream(outIn);
    final PrintWriter outWriter = new PrintWriter(outOut);

    final String[] commands = myEclipseExternalCompiler.createStartupCommand(chunk, compileContext, outputDir);
    final String[] modifiedCmds = JavacEmbeddedCompiler.createCommandsForEmbeddedCall(commands, EclipseCompiler.getCompilerClass());
    //for (int i = 0; i < modifiedCmds.length; i++) {
    //  String modifiedCmd = modifiedCmds[i];
    //  if (StringUtil.startsWithChar(modifiedCmd, '@') && modifiedCmd.contains(" ")) {
    //    modifiedCmds[i] = "@\"" + modifiedCmd.substring(1) + "\"";
    //  }
    //}


    Process process = new Process() {
      public OutputStream getOutputStream() {
        //noinspection HardCodedStringLiteral
        throw new UnsupportedOperationException("Not Implemented in: " + getClass().getName());
      }

      public InputStream getInputStream() {
        return outIn;
      }

      public InputStream getErrorStream() {
        return errIn;
      }

      public void destroy() {
      }

      public int waitFor() {
        try {
          URL url = new File("C:\\java\\eclipse\\plugins\\org.eclipse.jdt.core_3.1.0.jar").toURI().toURL();
          URLClassLoader classLoader = new URLClassLoader(new URL[]{url});
          Class<?> aClass = classLoader.loadClass(EclipseCompiler.getCompilerClass());
          Constructor<?> constructor = aClass.getDeclaredConstructor(PrintWriter.class, PrintWriter.class, boolean.class);
          Object compiler = constructor.newInstance(outWriter, errWriter, Boolean.FALSE);

          Method compileMethod = aClass.getDeclaredMethod("compile", String[].class);
          compileMethod.setAccessible(true);

          Object argument = modifiedCmds;
          Boolean result = (Boolean)compileMethod.invoke(compiler, argument);
          myExitCode = result.booleanValue() ? 0 : 1;
          errWriter.println(CompilerParsingThread.TERMINATION_STRING);
          errWriter.flush();

          return myExitCode;
        }
        catch (Exception e) {
          LOG.error(e);
          myExitCode = -1;
          return -1;
        }
      }

      public int exitValue() {
        return myExitCode;
      }
    };
    return process;
  }
}
