/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import org.apache.tools.ant.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.EmptyVisitor;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 15.05.11
 * Time: 21:36
 * To change this template use File | Settings | File Templates.
 */

public class InstrumentationUtil {
  public static PseudoClassLoader createPseudoClassLoader(final String classPath) throws MalformedURLException {
    final ArrayList urls = new ArrayList();
    for (StringTokenizer tokenizer = new StringTokenizer(classPath, File.pathSeparator); tokenizer.hasMoreTokens();) {
      final String s = tokenizer.nextToken();
      urls.add(new File(s).toURL());
    }
    final URL[] urlsArr = (URL[])urls.toArray(new URL[urls.size()]);
    return new PseudoClassLoader(urlsArr);
  }

  public static int getClassFileVersion(ClassReader reader) {
    final int[] classfileVersion = new int[1];
    reader.accept(new EmptyVisitor() {
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        classfileVersion[0] = version;
      }
    }, 0);

    return classfileVersion[0];
  }

  public static int getAsmClassWriterFlags(int version) {
    return version >= Opcodes.V1_6 && version != Opcodes.V1_1 ? ClassWriter.COMPUTE_FRAMES : ClassWriter.COMPUTE_MAXS;
  }

  public static abstract class FormInstrumenter {
    protected final List myNestedFormPathList;
    protected final File myDestDir;
    private final HashMap myClass2form = new HashMap();
    private List myFormFiles = null;

    public abstract void log(String msg, int option);

    public abstract void fireError(String msg);

    public abstract List getFormFiles();

    public void associate(String formFile, String classFile) {

    }

    private List formFiles() {
      if (myFormFiles == null) {
        myFormFiles = getFormFiles();
      }

      return myFormFiles;
    }

    protected FormInstrumenter(final File destDir, final List nestedFormPathList) {
      myNestedFormPathList = nestedFormPathList;
      myDestDir = destDir;
    }

    private File getClassFile(String className) {
      final String classOrInnerName = getClassOrInnerName(className);
      if (classOrInnerName == null) return null;
      return new File(myDestDir.getAbsolutePath(), classOrInnerName + ".class");
    }

    private String getClassOrInnerName(String className) {
      File classFile = new File(myDestDir.getAbsolutePath(), className + ".class");
      if (classFile.exists()) return className;
      int position = className.lastIndexOf('/');
      if (position == -1) return null;
      return getClassOrInnerName(className.substring(0, position) + '$' + className.substring(position + 1));
    }

    private class AntNestedFormLoader implements NestedFormLoader {
      private final PseudoClassLoader myLoader;
      private final List myNestedFormPathList;
      private final HashMap myFormCache = new HashMap();

      public AntNestedFormLoader(final PseudoClassLoader loader, List nestedFormPathList) {
        myLoader = loader;
        myNestedFormPathList = nestedFormPathList;
      }

      public LwRootContainer loadForm(String formFilePath) throws Exception {
        if (myFormCache.containsKey(formFilePath)) {
          return (LwRootContainer)myFormCache.get(formFilePath);
        }

        String lowerFormFilePath = formFilePath.toLowerCase();
        log("Searching for form " + lowerFormFilePath, Project.MSG_VERBOSE);

        for (Iterator iterator = formFiles().iterator(); iterator.hasNext();) {
          File file = (File)iterator.next();
          String name = file.getAbsolutePath().replace(File.separatorChar, '/').toLowerCase();
          log("Comparing with " + name, Project.MSG_VERBOSE);
          if (name.endsWith(lowerFormFilePath)) {
            return loadForm(formFilePath, new FileInputStream(file));
          }
        }

        if (myNestedFormPathList != null) {
          for (int i = 0; i < myNestedFormPathList.size(); i++) {
            PrefixedPath path = (PrefixedPath)myNestedFormPathList.get(i);
            File formFile = path.findFile(formFilePath);
            if (formFile != null) {
              return loadForm(formFilePath, new FileInputStream(formFile));
            }
          }
        }
        InputStream resourceStream = myLoader.getLoader ().getResourceAsStream(formFilePath);
        if (resourceStream != null) {
          return loadForm(formFilePath, resourceStream);
        }
        throw new Exception("Cannot find nested form file " + formFilePath);
      }

      private LwRootContainer loadForm(String formFileName, InputStream resourceStream) throws Exception {
        final LwRootContainer container = Utils.getRootContainer(resourceStream, null);
        myFormCache.put(formFileName, container);
        return container;
      }

      public String getClassToBindName(LwRootContainer container) {
        final String className = container.getClassToBind();
        String result = getClassOrInnerName(className.replace('.', '/'));
        if (result != null) return result.replace('/', '.');
        return className;
      }
    }

    public void instrumentForm(final File file, final PseudoClassLoader loader) {
      log("compiling form " + file.getAbsolutePath(), Project.MSG_VERBOSE);
      final LwRootContainer rootContainer;
      try {
        rootContainer = Utils.getRootContainer(file.toURL(), new CompiledClassPropertiesProvider(loader.getLoader ()));
      }
      catch (AlienFormFileException e) {
        // ignore non-IDEA forms
        return;
      }
      catch (Exception e) {
        fireError("Cannot process form file " + file.getAbsolutePath() + ". Reason: " + e);
        return;
      }

      final String classToBind = rootContainer.getClassToBind();
      if (classToBind == null) {
        return;
      }

      String name = classToBind.replace('.', '/');
      File classFile = getClassFile(name);
      if (classFile == null) {
        log(file.getAbsolutePath() + ": Class to bind does not exist: " + classToBind, Project.MSG_WARN);
        return;
      }

      final File alreadyProcessedForm = (File)myClass2form.get(classToBind);
      if (alreadyProcessedForm != null) {
        fireError(file.getAbsolutePath() +
                  ": " +
                  "The form is bound to the class " +
                  classToBind +
                  ".\n" +
                  "Another form " +
                  alreadyProcessedForm.getAbsolutePath() +
                  " is also bound to this class.");
        return;
      }
      myClass2form.put(classToBind, file);

      associate(file.getAbsolutePath(), name);

      try {
        int version;
        InputStream stream = new FileInputStream(classFile);
        try {
          version = getClassFileVersion(new ClassReader(stream));
        }
        finally {
          stream.close();
        }
        AntNestedFormLoader formLoader = new AntNestedFormLoader(loader, myNestedFormPathList);
        AntClassWriter classWriter = new AntClassWriter(InstrumentationUtil.getAsmClassWriterFlags(version), loader);
        final AsmCodeGenerator codeGenerator = new AsmCodeGenerator(rootContainer, loader.getLoader(), formLoader, false, classWriter);
        codeGenerator.patchFile(classFile);
        final FormErrorInfo[] warnings = codeGenerator.getWarnings();

        for (int j = 0; j < warnings.length; j++) {
          log(file.getAbsolutePath() + ": " + warnings[j].getErrorMessage(), Project.MSG_WARN);
        }
        final FormErrorInfo[] errors = codeGenerator.getErrors();
        if (errors.length > 0) {
          StringBuffer message = new StringBuffer();
          for (int j = 0; j < errors.length; j++) {
            if (message.length() > 0) {
              message.append("\n");
            }
            message.append(file.getAbsolutePath()).append(": ").append(errors[j].getErrorMessage());
          }
          fireError(message.toString());
        }
      }
      catch (Exception e) {
        fireError("Forms instrumentation failed for " + file.getAbsolutePath() + ": " + e.toString());
      }
    }
  }

  public static byte[] instrumentNotNull(final byte[] buffer, final PseudoClassLoader loader) {
    return instrumentNotNull(new ClassReader (buffer), loader);
  }

  private static byte[] instrumentNotNull(final ClassReader reader, final PseudoClassLoader loader) {
      int version = getClassFileVersion(reader);

      if (version >= Opcodes.V1_5) {
        ClassWriter writer = new AntClassWriter(getAsmClassWriterFlags(version), loader);

        final NotNullVerifyingInstrumenter instrumenter = new NotNullVerifyingInstrumenter(writer);
        reader.accept(instrumenter, 0);

        if (instrumenter.isModification()) {
          return writer.toByteArray();
        }
      }

      return null;
    }

  public static int instrumentNotNull(final File file, final PseudoClassLoader loader) throws IOException {
    int instrumented = 0;
    final String path = file.getPath();
    final FileInputStream inputStream = new FileInputStream(file);

    try {
      final ClassReader reader = new ClassReader(inputStream);
      final byte[] result = instrumentNotNull(reader, loader);

      if (result != null) {
        final FileOutputStream fileOutputStream = new FileOutputStream(path);
        try {
          fileOutputStream.write(result);
          instrumented++;
        }
        finally {
          fileOutputStream.close();
        }
      }
    }
    finally {
      inputStream.close();
    }

    return instrumented;
  }
}
