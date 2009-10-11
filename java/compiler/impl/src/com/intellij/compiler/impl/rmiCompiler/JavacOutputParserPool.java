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
package com.intellij.compiler.impl.rmiCompiler;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.javaCompiler.CompilerParsingThread;
import com.intellij.compiler.impl.javaCompiler.javac.JavacOutputParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.rt.compiler.JavacResourcesReader;
import com.intellij.util.ArrayUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 10, 2005
 */
public class JavacOutputParserPool {
  protected final Project myProject;
  private final CompileContext myContext;
  private final Map<Sdk, OutputParser> myProjectToParserMap = new HashMap<Sdk, OutputParser>();

  protected JavacOutputParserPool(Project project, final CompileContext context) {
    myProject = project;
    myContext = context;
  }

  public OutputParser getJavacOutputParser(Sdk jdk) throws IOException {
    OutputParser outputParser = myProjectToParserMap.get(jdk);
    if (outputParser == null) {
      outputParser = createJavacOutputParser(jdk);
      myProjectToParserMap.put(jdk, outputParser);
    }
    return outputParser;
  }

  private OutputParser createJavacOutputParser(final Sdk jdk) throws IOException {
    final JavacOutputParser outputParser = new JavacOutputParser(myProject);
    // first, need to setup the output parser
    final String[] setupCmdLine = ApplicationManager.getApplication().runReadAction(new Computable<String[]>() {
      public String[] compute() {
        return createParserSetupCommand(jdk);
      }
    });
    final Process setupProcess = Runtime.getRuntime().exec(setupCmdLine);

    final CompilerParsingThread setupProcessParsingThread = new CompilerParsingThread(setupProcess, outputParser, true, true,myContext);
    final Future<?> parsingThreadFuture = ApplicationManager.getApplication().executeOnPooledThread(setupProcessParsingThread);
    try {
      setupProcess.waitFor();
    }
    catch (InterruptedException ignored) {
    }
    finally {
      setupProcessParsingThread.setProcessTerminated(true);
    }
    try {
      parsingThreadFuture.get();
    }
    catch (InterruptedException e) {
    }
    catch (ExecutionException e) {
    }
    return outputParser;
  }

  private static String[] createParserSetupCommand(final Sdk jdk) {

    final VirtualFile homeDirectory = jdk.getHomeDirectory();
    if (homeDirectory == null) {
      throw new IllegalArgumentException(CompilerBundle.jdkHomeNotFoundMessage(jdk));
    }

    final List<String> commandLine = new ArrayList<String>();
    commandLine.add(((JavaSdkType)jdk.getSdkType()).getVMExecutablePath(jdk));

    CompilerUtil.addLocaleOptions(commandLine, false);

    //noinspection HardCodedStringLiteral
    commandLine.add("-classpath");
    commandLine.add(((JavaSdkType)jdk.getSdkType()).getToolsPath(jdk) + File.pathSeparator + JavaSdkUtil.getIdeaRtJarPath());

    commandLine.add(JavacResourcesReader.class.getName());

    return ArrayUtil.toStringArray(commandLine);
  }

}
