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
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.function.Supplier;
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

  private static volatile Map<String, List<InsertPoint>> myInsertPoints = new HashMap<>();

  private static boolean DEBUG = false;

  static {
    InsertPoint invokeLater = new InsertPoint("java/awt/event/InvocationEvent", "dispatch", "runnable", "Ljava/lang/Runnable;");
    myInsertPoints.put(invokeLater.myClassName, Collections.singletonList(invokeLater));
  }

  public static void premain(String args, Instrumentation instrumentation) throws IOException {
    ourInstrumentation = instrumentation;
    instrumentation.appendToBootstrapClassLoaderSearch(createTempJar("debugger-agent-storage.jar"));
    instrumentation.appendToSystemClassLoaderSearch(createTempJar("asm-all.jar"));
    instrumentation.addTransformer(new CaptureTransformer());
    DEBUG = "debug".equals(args);
    if (DEBUG) {
      CaptureStorage.setDebug(true);
      System.out.println("Capture agent: ready");
    }
  }

  private static <T> List<T> getNotNull(List<T> list) {
    return list != null ? list : Collections.emptyList();
  }

  private static class CaptureTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
      List<CapturePoint> capturePoints = getNotNull(myCapturePoints.get(className));
      List<InsertPoint> insertPoints = getNotNull(myInsertPoints.get(className));
      if (!capturePoints.isEmpty() || !insertPoints.isEmpty()) {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
        for (CapturePoint point : capturePoints) {
          try {
            reader.accept(new CaptureInstrumentor(Opcodes.ASM6, writer, point), 0);
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
        for (InsertPoint point : insertPoints) {
          try {
            reader.accept(new InsertInstrumentor(Opcodes.ASM6, writer, point), 0);
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
        byte[] bytes = writer.toByteArray();

        if (DEBUG) {
          try {
            Path path = new File("instrumented_" + className.replaceAll("/", "_") + ".class").toPath();
            Files.write(path, bytes);
          }
          catch (IOException e) {
            e.printStackTrace();
          }
        }

        return bytes;
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
        if (DEBUG) {
          System.out.println("Capture agent: instrumented capture point at " + capturePoint.myClassName + "." + name);
        }
        return new MethodVisitor(api, mv) {
          @Override
          public void visitCode() {
            visitVarInsn(Opcodes.ALOAD, capturePoint.myParamSlotId);
            visitMethodInsn(Opcodes.INVOKESTATIC, CaptureStorage.class.getName().replaceAll("\\.", "/"), "capture",
                            "(Ljava/lang/Object;)V", false);
            super.visitCode();
          }
        };
      }
      return mv;
    }
  }

  private static class InsertInstrumentor extends ClassVisitor {
    private InsertPoint myInsertPoint;
    Supplier<MethodVisitor> myVisitMethod = null;
    String myDesc;

    public InsertInstrumentor(int api, ClassVisitor cv, InsertPoint insertPoint) {
      super(api, cv);
      this.myInsertPoint = insertPoint;
    }

    private static String getNewName(String name) {
      return name + "$$$capture";
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
      if (myInsertPoint.myMethodName.equals(name)) {
        MethodVisitor mv = super.visitMethod(access, getNewName(name), desc, signature, exceptions);
        myDesc = desc;
        myVisitMethod = () -> super.visitMethod(access, name, desc, signature, exceptions);
        if (DEBUG) {
          System.out.println("Capture agent: instrumented insert point at " + myInsertPoint.myClassName + "." + name);
        }
        return mv;
      }
      return super.visitMethod(access, name, desc, signature, exceptions);
    }

    @Override
    public void visitEnd() {
      if (myVisitMethod != null) {
        MethodVisitor mv = myVisitMethod.get();

        Label start = new Label();
        mv.visitLabel(start);

        insertEnter(mv);

        mv.visitVarInsn(Opcodes.ALOAD, 0);
        // TODO: mv.loadArgs();
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, myInsertPoint.myClassName, getNewName(myInsertPoint.myMethodName), myDesc, false);

        Label end = new Label();
        mv.visitLabel(end);

        // regular exit
        insertExit(mv);
        mv.visitInsn(Opcodes.RETURN);

        Label catchLabel = new Label();
        mv.visitLabel(catchLabel);
        mv.visitTryCatchBlock(start, end, catchLabel, null);

        // exception exit
        insertExit(mv);
        mv.visitInsn(Opcodes.ATHROW);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
      }
    }

    private void insertEnter(MethodVisitor mv) {
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitFieldInsn(Opcodes.GETFIELD, myInsertPoint.myClassName, myInsertPoint.myField, myInsertPoint.myFieldDesc);
      mv.visitMethodInsn(Opcodes.INVOKESTATIC, CaptureStorage.class.getName().replaceAll("\\.", "/"), "insertEnter",
                         "(Ljava/lang/Object;)V", false);
    }

    private void insertExit(MethodVisitor mv) {
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitFieldInsn(Opcodes.GETFIELD, myInsertPoint.myClassName, myInsertPoint.myField, myInsertPoint.myFieldDesc);
      mv.visitMethodInsn(Opcodes.INVOKESTATIC, CaptureStorage.class.getName().replaceAll("\\.", "/"), "insertExit",
                         "(Ljava/lang/Object;)V", false);
    }
  }

  static class CapturePoint {
    final String myClassName;
    final String myMethodName;
    final int myParamSlotId;

    public CapturePoint(String className, String methodName, int paramSlotId) {
      this.myClassName = className;
      this.myMethodName = methodName;
      this.myParamSlotId = paramSlotId;
    }
  }

  static class InsertPoint {
    final String myClassName;
    final String myMethodName;
    final String myField;
    final String myFieldDesc;

    public InsertPoint(String className, String methodName, String field, String fieldDesc) {
      this.myClassName = className;
      this.myMethodName = methodName;
      this.myField = field;
      myFieldDesc = fieldDesc;
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
