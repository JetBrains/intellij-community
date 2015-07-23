/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.testme.instrumentation;

import org.jetbrains.org.objectweb.asm.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


public class TestDiscoveryInstrumentator {

  public static void premain(String argsString, Instrumentation instrumentation) throws Exception {
    instrumentation.addTransformer(new ClassFileTransformer() {
      private boolean computeFrames = computeFrames();

      public byte[] transform(ClassLoader loader,
                              String className,
                              Class classBeingRedefined,
                              ProtectionDomain protectionDomain,
                              byte[] classfileBuffer) throws IllegalClassFormatException {
        try {
          if (className == null) {
            return null;
          }
          if (loader == null) {
            // skip classes loaded by system classloader
            //System.out.println("Skipping " + className);
            return null;
          }
          if (className.endsWith(".class")) {
            className = className.substring(0, className.length() - 6);
          }
          className = className.replace('\\', '.').replace('/', '.');

          if (className.startsWith("com.intellij.rt.")
            || className.startsWith("com.intellij.util.lang.")
            || className.startsWith("com.intellij.util.containers.")
            || className.startsWith("com.intellij.openapi.util.text.")
            || className.startsWith("com.intellij.openapi.util.io.")
            || className.startsWith("java.")
            || className.startsWith("sun.")
            || className.startsWith("gnu.trove.")
            || className.startsWith("org.jetbrains.org.objectweb.asm.")
            || className.startsWith("org.apache.oro.text.regex.")
            || className.startsWith("org.jetbrains.testme.")
            || className.startsWith("org.apache.log4j.")
            || className.startsWith("org.junit.")
            || className.startsWith("com.sun.")
            || className.startsWith("junit.")
            || className.startsWith("jdk.internal.")
            || className.startsWith("com.intellij.junit3.")
            || className.startsWith("com.intellij.junit4.")) {
            return null;
          }
          //System.out.println(className);
          return instrument(classfileBuffer, className, loader, computeFrames);
        } catch (Throwable e) {
          e.printStackTrace();
        }
        return null;
      }

      private boolean computeFrames() {
        return System.getProperty("idea.coverage.no.frames") == null;
      }
    });
  }

  private final static AtomicInteger myInstrumentedClasses = new AtomicInteger();
  private final static AtomicInteger myInstrumentedMethods = new AtomicInteger();
  private final static AtomicLong myInstrumentedClassesTime = new AtomicLong();

  private static byte[] instrument(final byte[] classfileBuffer, final String className, ClassLoader loader, boolean computeFrames) {
    long started = System.nanoTime();
    final ClassReader cr = new ClassReader(classfileBuffer);
    final ClassWriter cw;
    if (computeFrames && false) { // frames calculation traverses hierarchy and makes instrumentation longer
      final int version = getClassFileVersion(cr);
      cw = getClassWriter(version >= Opcodes.V1_6 && version != Opcodes.V1_1 ? ClassWriter.COMPUTE_FRAMES : ClassWriter.COMPUTE_MAXS, loader);
    } else {
      cw = getClassWriter(ClassWriter.COMPUTE_MAXS, loader);
    }

    final List<String> instrumentedMethods = new ArrayList<String>();

    final ClassVisitor instrumentedMethodCounter =  new ClassVisitor(Opcodes.ASM5) {
      final InstrumentedMethodsFilter methodsFilter = new InstrumentedMethodsFilter(className);
      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        methodsFilter.visit(version, access, name, signature, superName, interfaces);
        super.visit(version, access, name, signature, superName, interfaces);
      }

      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if (methodsFilter.shouldVisitMethod(access, name, desc, signature, exceptions)) {
          if ("<init>".equals(name)) {
            final int slashPos = className.lastIndexOf('.');
            final int $Pos = className.lastIndexOf('$');
            name = className.substring(Math.max(slashPos, $Pos) + 1);
          }
          instrumentedMethods.add(name);
        }
        return super.visitMethod(access, name, desc, signature, exceptions);
      }
    };

    cr.accept(instrumentedMethodCounter, 0);

    // todo there are duplicates in array of instrumented methods
    final ClassVisitor cv =  new Instrumenter(cw, className, instrumentedMethods.toArray(new String[instrumentedMethods.size()]));
    cr.accept(cv, 0);
    byte[] bytes = cw.toByteArray();

    long time = myInstrumentedClassesTime.addAndGet(System.nanoTime() - started);
    int classes = myInstrumentedClasses.incrementAndGet();
    int methods = myInstrumentedMethods.addAndGet(instrumentedMethods.size());
    //if (classes % 1000 == 0) {
    //  System.out.println("Done instrumenting " + classes + ", methods:" + methods + " for " + (time / 1000000));
    //}

    if (false) {
      try {
        FileOutputStream fileOutputStream = new FileOutputStream("transformed-" + className);
        try {
          fileOutputStream.write(bytes);
          fileOutputStream.close();
        } finally {
          fileOutputStream.close();
        }
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }
    return bytes;
  }

  private static ClassWriter getClassWriter(int flags, final ClassLoader classLoader) {
    return new MyClassWriter(flags, classLoader);
  }

  public static int getClassFileVersion(ClassReader reader) {
    final int[] classFileVersion = new int[1];
    reader.accept(new ClassVisitor(Opcodes.ASM5) {
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        classFileVersion[0] = version;
      }
    }, 0);
    return classFileVersion[0];
  }

  private static class MyClassWriter extends ClassWriter {
    public static final String JAVA_LANG_OBJECT = "java/lang/Object";
    private final ClassLoader classLoader;

    public MyClassWriter(int flags, ClassLoader classLoader) {
      super(flags);
      this.classLoader = classLoader;
    }

    protected String getCommonSuperClass(String type1, String type2) {
      try {
        ClassReader info1 = typeInfo(type1);
        ClassReader info2 = typeInfo(type2);
        String
        superType = checkImplementInterface(type1, type2, info1, info2);
        if (superType != null) return superType;
        superType = checkImplementInterface(type2, type1, info2, info1);
        if (superType != null) return superType;

        StringBuilder b1 = typeAncestors(type1, info1);
        StringBuilder b2 = typeAncestors(type2, info2);
        String result = JAVA_LANG_OBJECT;
        int end1 = b1.length();
        int end2 = b2.length();
        while (true) {
          int start1 = b1.lastIndexOf(";", end1 - 1);
          int start2 = b2.lastIndexOf(";", end2 - 1);
          if (start1 != -1 && start2 != -1 && end1 - start1 == end2 - start2) {
            String p1 = b1.substring(start1 + 1, end1);
            String p2 = b2.substring(start2 + 1, end2);
            if (p1.equals(p2)) {
              result = p1;
              end1 = start1;
              end2 = start2;
            } else {
              return result;
            }
          } else {
            return result;
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e.toString());
      }
    }

    private String checkImplementInterface(String type1, String type2, ClassReader info1, ClassReader info2) throws IOException {
      if ((info1.getAccess() & Opcodes.ACC_INTERFACE) != 0) {
        if (typeImplements(type2, info2, type1)) {
          return type1;
        }
        return JAVA_LANG_OBJECT;
      }
      return null;
    }

    private StringBuilder typeAncestors(String type, ClassReader info) throws IOException {
      StringBuilder b = new StringBuilder();
      while (!JAVA_LANG_OBJECT.equals(type)) {
        b.append(';').append(type);
        type = info.getSuperName();
        info = typeInfo(type);
      }
      return b;
    }

    private boolean typeImplements(String type, ClassReader classReader, String interfaceName) throws IOException {
      while (!JAVA_LANG_OBJECT.equals(type)) {
        String[] itfs = classReader.getInterfaces();
        for (int i = 0; i < itfs.length; ++i) {
          if (itfs[i].equals(interfaceName)) {
            return true;
          }
        }
        for (int i = 0; i < itfs.length; ++i) {
          if (typeImplements(itfs[i], typeInfo(itfs[i]), interfaceName)) {
            return true;
          }
        }
        type = classReader.getSuperName();
        classReader = typeInfo(type);
      }
      return false;
    }

    private ClassReader typeInfo(final String type) throws IOException {
      InputStream is = classLoader.getResourceAsStream(type + ".class");
      if (is == null) System.out.println(classLoader + "," + type + ".class");
      try {
        return new ClassReader(is);
      } finally {
        is.close();
      }
    }
  }
}
