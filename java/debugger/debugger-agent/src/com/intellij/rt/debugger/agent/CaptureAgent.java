/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.rt.debugger.agent;

import org.jetbrains.org.objectweb.asm.*;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.jar.JarFile;

/**
 * @author egor
 */
public class CaptureAgent {
  private static Instrumentation ourInstrumentation;
  private static volatile Map<String, List<CapturePoint>> myCapturePoints = new HashMap<>();
  static {
    CapturePoint invokeLater = new CapturePoint("javax/swing/SwingUtilities", "invokeLater", 0);
    myCapturePoints.put(invokeLater.myClassName, Collections.singletonList(invokeLater));
  }

  public static void premain(String args, Instrumentation instrumentation) throws IOException {
    ourInstrumentation = instrumentation;
    instrumentation.appendToBootstrapClassLoaderSearch(createTempJar("debugger-agent-storage.jar"));
    instrumentation.appendToSystemClassLoaderSearch(createTempJar("asm-all.jar"));
    instrumentation.addTransformer(new CaptureTransformer());
    System.out.println("Capture agent: ready");
  }

  private static class CaptureTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
      List<CapturePoint> capturePoints = myCapturePoints.get(className);
      if (!capturePoints.isEmpty()) {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        for (CapturePoint point : capturePoints) {
          reader.accept(new CaptureInstrumentor(Opcodes.ASM6, writer, point), 0);
        }
        return writer.toByteArray();
      }
      return null;
    }
  }

  private static class CaptureInstrumentor extends ClassVisitor {
    private CapturePoint capturePoint;

    public CaptureInstrumentor(int api, ClassVisitor cv, CapturePoint capturePoint) {
      super(api, cv);
      this.capturePoint = capturePoint;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
      MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
      if (capturePoint.myMethodName.equals(name)) {
        System.out.println("Capture agent: instrumented " + capturePoint.myClassName + "." + name);
        return new MethodVisitor(api, mv) {
          @Override
          public void visitCode() {
            visitFieldInsn(Opcodes.GETSTATIC, CaptureStorage.class.getName().replaceAll("\\.", "/"), "STORAGE", "Ljava/util/Map;");
            visitVarInsn(Opcodes.ALOAD, capturePoint.myParamSlotId);
            visitTypeInsn(Opcodes.NEW, "java/lang/Exception");
            visitInsn(Opcodes.DUP);
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Exception", "<init>", "()V", false);
            visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "put",
                            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
            visitInsn(Opcodes.POP);
            super.visitCode();
          }
        };
      }
      return mv;
    }
  }

  static class CapturePoint {
    final String myClassName;
    final String myMethodName;
    final int myParamSlotId;

    public CapturePoint(String myClassName, String myMethodName, int myParamSlotId) {
      this.myClassName = myClassName;
      this.myMethodName = myMethodName;
      this.myParamSlotId = myParamSlotId;
    }
  }

  // TODO: these files are not deleted even if deleteOnExit or anything else, we need to separate jars
  private static JarFile createTempJar(String name) throws IOException {
    File tempJar = File.createTempFile("Capture", ".jar");
    Files.copy(CaptureAgent.class.getClassLoader().getResourceAsStream(name), tempJar.toPath(),
               StandardCopyOption.REPLACE_EXISTING);
    JarFile res = new JarFile(tempJar);
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        try {
          res.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
        tempJar.delete();
      }
    });
    return res;
  }

  // to be run from the debugger
  @SuppressWarnings("unused")
  public static void setCapturePoints(Object[][] capturePoints) throws UnmodifiableClassException {
    Set<String> classNames = new HashSet<>(myCapturePoints.keySet());

    Map<String, List<CapturePoint>> points = new HashMap<>();
    for (Object[] capturePoint : capturePoints) {
      String className = (String)capturePoint[0];
      classNames.add(className);
      List<CapturePoint> currentPoints = points.get(className);
      if (currentPoints == null) {
        currentPoints = new ArrayList<>();
        points.put(className, currentPoints);
      }
      currentPoints.add(new CapturePoint(className, (String)capturePoint[1], (int)capturePoint[2]));
    }
    myCapturePoints = points;

    List<Class> classes = new ArrayList<>(capturePoints.length);
    for (String name : classNames) {
      try {
        classes.add(Class.forName(name));
      }
      catch (ClassNotFoundException e) {
        e.printStackTrace();
      }
    }
    ourInstrumentation.retransformClasses(classes.toArray(new Class[0]));
  }
}
