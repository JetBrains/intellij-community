/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.ant;

import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import com.intellij.uiDesigner.compiler.*;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.types.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;

public final class Javac2 extends Javac{
  private ArrayList myFormFiles;

  public Javac2(){
  }

  protected void compile(){
    // compile java
    super.compile();

    // we instrument every file, because we cannot find which files should not be instrumented without dependency storage
    final ArrayList formsToInstrument = myFormFiles;

    if (formsToInstrument.size() == 0){
      log("No forms to instrument found", Project.MSG_VERBOSE);
      return;
    }


    final StringBuffer classPathBuffer = new StringBuffer();

    classPathBuffer.append(getDestdir().getAbsolutePath());

    final Path classpath = getClasspath();
    final String[] pathElements = classpath.list();
    for (int i = 0; i < pathElements.length; i++) {
      final String pathElement = pathElements[i];
      classPathBuffer.append(File.pathSeparator);
      classPathBuffer.append(pathElement);
    }

    classPathBuffer.append(File.pathSeparator);
    classPathBuffer.append(getInternalClassPath());

    final String classPath = classPathBuffer.toString();
    log("classpath=" + classPath, Project.MSG_INFO);

    final ClassLoader loader;
    try {
      loader = createClassLoader(classPath);
    }
    catch (MalformedURLException e) {
      fireError(e.getMessage());
      return;
    }

    final HashMap class2form = new HashMap();

    for (int i = 0; i < formsToInstrument.size(); i++) {
      final File formFile = (File)formsToInstrument.get(i);

      log("compiling form " + formFile.getAbsolutePath(), Project.MSG_VERBOSE);

      final byte[] bytes = new byte[(int)formFile.length()];
      try {
        final FileInputStream fileReader = new FileInputStream(formFile);
        fileReader.read(bytes);
        fileReader.close();
      }
      catch (IOException e) {
        fireError(e.getMessage());
        continue;
      }

      final String formFileContent;
      try {
        formFileContent = new String(bytes, "utf8");
      }
      catch (UnsupportedEncodingException e) {
        fireError(e.getMessage());
        continue;
      }

      final LwRootContainer rootContainer;
      try {
        rootContainer = Utils.getRootContainer(formFileContent, new CompiledClassPropertiesProvider(loader));
      }
      catch (AlienFormFileException e) {
        // ignore non-IDEA forms
        continue;
      }
      catch (Exception e) {
        fireError("Cannot process form file " + formFile.getAbsolutePath() + ". Reason: " + e);
        continue;
      }

      final String classToBind = rootContainer.getClassToBind();
      if (classToBind == null) {
        continue;
      }

      String name = classToBind.replace('.','/');
      File classFile = getClassFile(name);
      if (classFile == null) {
        log(formFile.getAbsolutePath() + ": Class to bind does not exist: " + classToBind, Project.MSG_WARN);
        continue;
      }

      final File alreadyProcessedForm = (File)class2form.get(classToBind);
      if (alreadyProcessedForm != null) {
        fireError(
          formFile.getAbsolutePath() + ": " +
          "The form is bound to the class " + classToBind + ".\n" +
          "Another form " + alreadyProcessedForm.getAbsolutePath() + " is also bound to this class."
        );
        continue;
      }
      class2form.put(classToBind, formFile);

      final AsmCodeGenerator codeGenerator = new AsmCodeGenerator(rootContainer, loader,
                                                                  new AntNestedFormLoader(loader), false);
      codeGenerator.patchFile(classFile);
      final FormErrorInfo[] warnings = codeGenerator.getWarnings();

      for (int j = 0; j < warnings.length; j++) {
        log(formFile.getAbsolutePath() + ": " + warnings[j].getErrorMessage(), Project.MSG_WARN);
      }
      final FormErrorInfo[] errors = codeGenerator.getErrors();
      if (errors.length > 0) {
        StringBuffer message = new StringBuffer();
        for (int j = 0; j < errors.length; j++) {
          if (message.length() > 0) {
            message.append("\n");
          }
          message.append(formFile.getAbsolutePath()).append(": ").append(errors[j].getErrorMessage());
        }
        fireError(message.toString());
      }
    }

    //NotNull instrumentation
    instrumentNotNull(getDestdir());
  }

  private void instrumentNotNull(File dir) {
    final File[] files = dir.listFiles();
    for (int i = 0; i < files.length; i++) {
      File file = files[i];
      final String name = file.getName();
      if (name.endsWith(".class")) {
        final String path = file.getPath();
        log("Adding @NotNull assertions to " + path, Project.MSG_VERBOSE);
        try {
          final FileInputStream inputStream = new FileInputStream(file);
          ClassReader reader = new ClassReader(inputStream);
          ClassWriter writer = new ClassWriter(true);

          final NotNullVerifyingInstrumenter instrumenter = new NotNullVerifyingInstrumenter(writer);
          reader.accept(instrumenter, false);
          if (instrumenter.isModification()) {
            new FileOutputStream(path).write(writer.toByteArray());
          }
        }
        catch (IOException e) {
          log("Failed to instrument @NotNull assertion: " + e.getMessage(), Project.MSG_WARN);
        }
      } else if (file.isDirectory()) {
        instrumentNotNull(file);
      }
    }
  }

  private String getInternalClassPath() {
    String class_path = System.getProperty("java.class.path");
    String boot_path  = System.getProperty("sun.boot.class.path");
    String ext_path   = System.getProperty("java.ext.dirs");

    ArrayList list = new ArrayList();

    getPathComponents(class_path, list);
    getPathComponents(boot_path, list);

    ArrayList dirs = new ArrayList();
    getPathComponents(ext_path, dirs);

    for(Iterator e = dirs.iterator(); e.hasNext(); ) {
      File     ext_dir    = new File((String)e.next());
      String[] extensions = ext_dir.list(new FilenameFilter() {
        public boolean accept(File dir, String name) {
          name = name.toLowerCase();
          return name.endsWith(".zip") || name.endsWith(".jar");
        }
      });

      if(extensions != null)
        for(int i=0; i < extensions.length; i++)
          list.add(ext_path + File.separatorChar + extensions[i]);
    }

    StringBuffer buf = new StringBuffer();

    for(Iterator e = list.iterator(); e.hasNext(); ) {
      buf.append((String)e.next());

      if(e.hasNext())
        buf.append(File.pathSeparatorChar);
    }

    return buf.toString().intern();
  }

  private static void getPathComponents(String path, ArrayList list) {
    if(path != null) {
      StringTokenizer tok = new StringTokenizer(path, File.pathSeparator);

      while(tok.hasMoreTokens()) {
        String name = tok.nextToken();
        File   file = new File(name);

	if(file.exists())
	  list.add(name);
      }
    }
  }

  private void fireError(final String message) {
    if (failOnError) {
      throw new BuildException(message, getLocation());
    }
    else {
      log(message, Project.MSG_ERR);
    }
  }

  private File getClassFile(String className) {
    File classFile = new File(getDestdir().getAbsolutePath(), className + ".class");
    if (classFile.exists()) return classFile;
    int position = className.lastIndexOf('/');
    if (position == -1) return null;
    return getClassFile(className.substring(0, position) + '$' + className.substring(position + 1));
  }

  private static URLClassLoader createClassLoader(final String classPath) throws MalformedURLException{
    final ArrayList urls = new ArrayList();
    for (StringTokenizer tokenizer = new StringTokenizer(classPath, File.pathSeparator); tokenizer.hasMoreTokens();) {
      final String s = tokenizer.nextToken();
      urls.add(new File(s).toURL());
    }
    final URL[] urlsArr = (URL[])urls.toArray(new URL[urls.size()]);
    return new URLClassLoader(urlsArr, null);
  }
  
  protected void resetFileLists(){
    super.resetFileLists();
    myFormFiles = new ArrayList();
  }

  protected void scanDir(final File srcDir, final File destDir, final String[] files) {
    super.scanDir(srcDir, destDir, files);
    for (int i = 0; i < files.length; i++) {
      final String file = files[i];
      if (file.endsWith(".form")) {
        log("Found form file " + file, Project.MSG_VERBOSE);
        myFormFiles.add(new File(srcDir, file));
      }
    }
  }

  private class AntNestedFormLoader implements NestedFormLoader {
    private ClassLoader myLoader;
    private HashMap myFormCache = new HashMap();

    public AntNestedFormLoader(final ClassLoader loader) {
      myLoader = loader;
    }

    public LwRootContainer loadForm(String formFileName) throws Exception {
      if (myFormCache.containsKey(formFileName)) {
        return (LwRootContainer) myFormCache.get(formFileName);
      }
      String formFileFullName = formFileName.toLowerCase();
      log("Searching for form " + formFileFullName, Project.MSG_VERBOSE);
      for (Iterator iterator = myFormFiles.iterator(); iterator.hasNext();) {
        File file = (File)iterator.next();
        String name = file.getAbsolutePath().replace(File.separatorChar, '/').toLowerCase();
        log("Comparing with " + name, Project.MSG_VERBOSE);
        if (name.endsWith(formFileFullName)) {
          InputStream formInputStream = new FileInputStream(file);
          final LwRootContainer container = Utils.getRootContainer(formInputStream, null);
          myFormCache.put(formFileName, container);
          return container;
        }
      }
      InputStream resourceStream = myLoader.getResourceAsStream("/" + formFileName + ".form");
      if (resourceStream != null) {
        final LwRootContainer container = Utils.getRootContainer(resourceStream, null);
        myFormCache.put(formFileName, container);
        return container;
      }
      throw new Exception("Cannot find nested form file " + formFileName);
    }
  }
}
