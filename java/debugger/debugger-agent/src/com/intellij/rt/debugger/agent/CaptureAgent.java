// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.debugger.agent;

import org.jetbrains.org.objectweb.asm.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.jar.JarFile;

/**
 * @author egor
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public class CaptureAgent {
  private static Instrumentation ourInstrumentation;
  private static boolean DEBUG = false;

  private static Map<String, List<CapturePoint>> myCapturePoints = new HashMap<String, List<CapturePoint>>();
  private static Map<String, List<InsertPoint>> myInsertPoints = new HashMap<String, List<InsertPoint>>();

  public static void premain(String args, Instrumentation instrumentation) {
    ourInstrumentation = instrumentation;
    try {
      String asmPath = readSettings(args);

      if (asmPath == null) {
        return;
      }

      try {
        instrumentation.appendToSystemClassLoaderSearch(new JarFile(asmPath));
      }
      catch (Exception e) {
        String report = "Capture agent: unable to use the provided asm lib";
        try {
          Class.forName("org.jetbrains.org.objectweb.asm.MethodVisitor");
          System.out.println(report + ", will use asm from the classpath");
        }
        catch (ClassNotFoundException e1) {
          System.out.println(report + ", exiting");
          return;
        }
      }

      instrumentation.addTransformer(new CaptureTransformer());

      // Trying to reinstrument java.lang.Thread
      // fails with dcevm, does not work with other vms :(
      //for (Class aClass : instrumentation.getAllLoadedClasses()) {
      //  String name = aClass.getName().replaceAll("\\.", "/");
      //  if (myCapturePoints.containsKey(name) || myInsertPoints.containsKey(name)) {
      //    try {
      //      instrumentation.retransformClasses(aClass);
      //    }
      //    catch (UnmodifiableClassException e) {
      //      e.printStackTrace();
      //    }
      //  }
      //}

      setupJboss();

      if (DEBUG) {
        System.out.println("Capture agent: ready");
      }
    }
    catch (Throwable e) {
      System.out.println("Capture agent: unknown exception");
      e.printStackTrace();
    }
  }

  private static void setupJboss() {
    String modulesKey = "jboss.modules.system.pkgs";
    String property = System.getProperty(modulesKey, "");
    if (!property.isEmpty()) {
      property += ",";
    }
    property += "com.intellij.rt";
    System.setProperty(modulesKey, property);
  }

  private static String readSettings(String path) {
    FileReader reader = null;
    try {
      reader = new FileReader(path);
      Properties properties = new Properties();
      properties.load(reader);

      DEBUG = Boolean.parseBoolean(properties.getProperty("debug", "false"));
      if (DEBUG) {
        CaptureStorage.setDebug(true);
      }

      if (Boolean.parseBoolean(properties.getProperty("disabled", "false"))) {
        CaptureStorage.setEnabled(false);
      }

      boolean deleteSettings = Boolean.parseBoolean(properties.getProperty("deleteSettings", "true"));

      String asmPath = properties.getProperty("asm-lib");
      if (asmPath == null) {
        System.out.println("Capture agent: asm path is not specified, exiting");
        return null;
      }

      Enumeration<?> propNames = properties.propertyNames();
      while (propNames.hasMoreElements()) {
        String propName = (String)propNames.nextElement();
        if (propName.startsWith("capture")) {
          addPoint(true, properties.getProperty(propName));
        }
        else if (propName.startsWith("insert")) {
          addPoint(false, properties.getProperty(propName));
        }
      }

      // delete settings file only if it was read correctly
      if (deleteSettings) {
        new File(path).delete();
      }
      return asmPath;
    }
    catch (IOException e) {
      System.out.println("Capture agent: unable to read settings");
      e.printStackTrace();
    }
    finally {
      if (reader != null) {
        try {
          reader.close();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return null;
  }

  private static <T> List<T> getNotNull(List<T> list) {
    return list != null ? list : Collections.<T>emptyList();
  }

  private static class CaptureTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
      if (className != null) {
        List<CapturePoint> capturePoints = getNotNull(myCapturePoints.get(className));
        List<InsertPoint> insertPoints = getNotNull(myInsertPoints.get(className));
        if (!capturePoints.isEmpty() || !insertPoints.isEmpty()) {
          try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);

            reader.accept(new CaptureInstrumentor(Opcodes.API_VERSION, writer, capturePoints, insertPoints), 0);
            byte[] bytes = writer.toByteArray();

            if (DEBUG) {
              try {
                FileOutputStream stream = new FileOutputStream("instrumented_" + className.replaceAll("/", "_") + ".class");
                try {
                  stream.write(bytes);
                }
                finally {
                  stream.close();
                }
              }
              catch (IOException e) {
                e.printStackTrace();
              }
            }

            return bytes;
          }
          catch (Exception e) {
            e.printStackTrace();
          }
        }
      }
      return null;
    }
  }

  private static class CaptureInstrumentor extends ClassVisitor {
    private final List<CapturePoint> myCapturePoints;
    private final List<InsertPoint> myInsertPoints;

    public CaptureInstrumentor(int api, ClassVisitor cv, List<CapturePoint> capturePoints, List<InsertPoint> insertPoints) {
      super(api, cv);
      this.myCapturePoints = capturePoints;
      this.myInsertPoints = insertPoints;
    }

    private static String getNewName(String name) {
      return name + "$$$capture";
    }

    private static String getMethodDisplayName(String className, String methodName, String desc) {
      return className + "." + methodName + desc;
    }

    @Override
    public MethodVisitor visitMethod(final int access, String name, final String desc, String signature, String[] exceptions) {
      if ((access & Opcodes.ACC_BRIDGE) == 0) {
        for (final CapturePoint capturePoint : myCapturePoints) {
          if (capturePoint.myMethodName.equals(name)) {
            final String methodDisplayName = getMethodDisplayName(capturePoint.myClassName, name, desc);
            if (DEBUG) {
              System.out.println("Capture agent: instrumented capture point at " + methodDisplayName);
            }
            // for constructors and "this" key - move capture to the end
            if ("<init>".equals(name) && capturePoint.myKeyProvider == THIS_KEY_PROVIDER) {
              return new MethodVisitor(api, super.visitMethod(access, name, desc, signature, exceptions)) {
                @Override
                public void visitInsn(int opcode) {
                  if (opcode == Opcodes.RETURN) {
                    capture(mv, capturePoint.myKeyProvider, (access & Opcodes.ACC_STATIC) != 0,
                            Type.getMethodType(desc).getArgumentTypes(), methodDisplayName);
                  }
                  super.visitInsn(opcode);
                }
              };
            }
            else {
              return new MethodVisitor(api, super.visitMethod(access, name, desc, signature, exceptions)) {
                @Override
                public void visitCode() {
                  capture(mv, capturePoint.myKeyProvider, (access & Opcodes.ACC_STATIC) != 0, Type.getMethodType(desc).getArgumentTypes(),
                          methodDisplayName);
                  super.visitCode();
                }
              };
            }
          }
        }

        for (InsertPoint insertPoint : myInsertPoints) {
          if (insertPoint.myMethodName.equals(name)) {
            String methodDisplayName = getMethodDisplayName(insertPoint.myClassName, name, desc);
            if (DEBUG) {
              System.out.println("Capture agent: instrumented insert point at " + methodDisplayName);
            }
            generateWrapper(access, name, desc, signature, exceptions, insertPoint, methodDisplayName);
            return super.visitMethod(access, getNewName(name), desc, signature, exceptions);
          }
        }
      }
      return super.visitMethod(access, name, desc, signature, exceptions);
    }

    private void generateWrapper(int access,
                                 String name,
                                 String desc,
                                 String signature,
                                 String[] exceptions,
                                 InsertPoint insertPoint,
                                 String methodDisplayName) {
      MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

      Label start = new Label();
      mv.visitLabel(start);

      boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
      Type[] argumentTypes = Type.getMethodType(desc).getArgumentTypes();

      insertEnter(mv, insertPoint.myKeyProvider, isStatic, argumentTypes, methodDisplayName);

      // this
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      // params
      int index = isStatic ? 0 : 1;
      for (Type t : argumentTypes) {
        mv.visitVarInsn(t.getOpcode(Opcodes.ILOAD), index);
        index += t.getSize();
      }
      // original call
      mv.visitMethodInsn(isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKESPECIAL,
                         insertPoint.myClassName, getNewName(insertPoint.myMethodName), desc, false);

      Label end = new Label();
      mv.visitLabel(end);

      // regular exit
      insertExit(mv, insertPoint.myKeyProvider, isStatic, argumentTypes, methodDisplayName);
      mv.visitInsn(Type.getReturnType(desc).getOpcode(Opcodes.IRETURN));

      Label catchLabel = new Label();
      mv.visitLabel(catchLabel);
      mv.visitTryCatchBlock(start, end, catchLabel, null);

      // exception exit
      insertExit(mv, insertPoint.myKeyProvider, isStatic, argumentTypes, methodDisplayName);
      mv.visitInsn(Opcodes.ATHROW);

      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    private static void capture(MethodVisitor mv,
                                KeyProvider keyProvider,
                                boolean isStatic,
                                Type[] argumentTypes,
                                String methodDisplayName) {
      storageCall(mv, keyProvider, isStatic, argumentTypes, "capture", methodDisplayName);
    }

    private static void insertEnter(MethodVisitor mv,
                                    KeyProvider keyProvider,
                                    boolean isStatic,
                                    Type[] argumentTypes,
                                    String methodDisplayName) {
      storageCall(mv, keyProvider, isStatic, argumentTypes, "insertEnter", methodDisplayName);
    }

    private static void insertExit(MethodVisitor mv,
                                   KeyProvider keyProvider,
                                   boolean isStatic,
                                   Type[] argumentTypes,
                                   String methodDisplayName) {
      storageCall(mv, keyProvider, isStatic, argumentTypes, "insertExit", methodDisplayName);
    }

    private static void storageCall(MethodVisitor mv,
                                    KeyProvider keyProvider,
                                    boolean isStatic,
                                    Type[] argumentTypes,
                                    String storageMethodName,
                                    String methodDisplayName) {
      keyProvider.loadKey(mv, isStatic, argumentTypes, methodDisplayName);
      mv.visitMethodInsn(Opcodes.INVOKESTATIC, CaptureStorage.class.getName().replaceAll("\\.", "/"), storageMethodName,
                         "(Ljava/lang/Object;)V", false);
    }
  }

  static class CapturePoint {
    final String myClassName;
    final String myMethodName;
    final KeyProvider myKeyProvider;

    public CapturePoint(String className, String methodName, KeyProvider keyProvider) {
      this.myClassName = className;
      this.myMethodName = methodName;
      this.myKeyProvider = keyProvider;
    }
  }

  static class InsertPoint {
    final String myClassName;
    final String myMethodName;
    final KeyProvider myKeyProvider;

    public InsertPoint(String className, String methodName, KeyProvider keyProvider) {
      this.myClassName = className;
      this.myMethodName = methodName;
      this.myKeyProvider = keyProvider;
    }
  }

  // to be run from the debugger
  @SuppressWarnings("unused")
  public static void setCapturePoints(Object[][] capturePoints) throws UnmodifiableClassException {
    Set<String> classNames = new HashSet<String>(myCapturePoints.keySet());

    Map<String, List<CapturePoint>> points = new HashMap<String, List<CapturePoint>>();
    for (Object[] capturePoint : capturePoints) {
      String className = (String)capturePoint[0];
      classNames.add(className);
      List<CapturePoint> currentPoints = points.get(className);
      if (currentPoints == null) {
        currentPoints = new ArrayList<CapturePoint>();
        points.put(className, currentPoints);
      }
      //currentPoints.add(new CapturePoint(className, (String)capturePoint[1], (int)capturePoint[2]));
    }
    myCapturePoints = points;

    List<Class> classes = new ArrayList<Class>(capturePoints.length);
    for (String name : classNames) {
      try {
        classes.add(Class.forName(name));
      }
      catch (ClassNotFoundException e) {
        e.printStackTrace();
      }
    }
    //noinspection SSBasedInspection
    ourInstrumentation.retransformClasses(classes.toArray(new Class[0]));
  }

  private static void addPoint(boolean capture, String line) {
    String[] split = line.split(" ");
    KeyProvider keyProvider = createKeyProvider(Arrays.copyOfRange(split, 2, split.length));
    if (capture) {
      addCapturePoint(split[0], split[1], keyProvider);
    }
    else {
      addInsertPoint(split[0], split[1], keyProvider);
    }
  }

  private static void addCapturePoint(String className, String methodName, KeyProvider keyProvider) {
    List<CapturePoint> points = myCapturePoints.get(className);
    if (points == null) {
      points = new ArrayList<CapturePoint>();
      myCapturePoints.put(className, points);
    }
    points.add(new CapturePoint(className, methodName, keyProvider));
  }

  private static void addInsertPoint(String className, String methodName, KeyProvider keyProvider) {
    List<InsertPoint> points = myInsertPoints.get(className);
    if (points == null) {
      points = new ArrayList<InsertPoint>();
      myInsertPoints.put(className, points);
    }
    points.add(new InsertPoint(className, methodName, keyProvider));
  }

  static final KeyProvider THIS_KEY_PROVIDER = new KeyProvider() {
    @Override
    public void loadKey(MethodVisitor mv, boolean isStatic, Type[] argumentTypes, String methodDisplayName) {
      if (isStatic) {
        throw new IllegalStateException("This is not available in a static method " + methodDisplayName);
      }
      mv.visitVarInsn(Opcodes.ALOAD, 0);
    }
  };

  private static KeyProvider createKeyProvider(String[] line) {
    if ("this".equals(line[0])) {
      return THIS_KEY_PROVIDER;
    }
    if (isNumber(line[0])) {
      try {
        return new ParamKeyProvider(Integer.parseInt(line[0]));
      }
      catch (NumberFormatException ignored) {
      }
    }
    return new FieldKeyProvider(line[0], line[1], line[2]);
  }

  private static boolean isNumber(String s) {
    if (s == null) return false;
    for (int i = 0; i < s.length(); ++i) {
      if (!Character.isDigit(s.charAt(i))) return false;
    }
    return true;
  }

  private interface KeyProvider {
    void loadKey(MethodVisitor mv, boolean isStatic, Type[] argumentTypes, String methodDisplayName);
  }

  private static class FieldKeyProvider implements KeyProvider {
    String myClassName;
    String myFieldName;
    String myFieldDesc;

    public FieldKeyProvider(String className, String fieldName, String fieldDesc) {
      myClassName = className;
      myFieldName = fieldName;
      myFieldDesc = fieldDesc;
    }

    @Override
    public void loadKey(MethodVisitor mv, boolean isStatic, Type[] argumentTypes, String methodDisplayName) {
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitFieldInsn(Opcodes.GETFIELD, myClassName, myFieldName, myFieldDesc);
    }
  }

  private static class ParamKeyProvider implements KeyProvider {
    int myIdx;

    public ParamKeyProvider(int idx) {
      myIdx = idx;
    }

    @Override
    public void loadKey(MethodVisitor mv, boolean isStatic, Type[] argumentTypes, String methodDisplayName) {
      int index = isStatic ? 0 : 1;
      if (myIdx >= argumentTypes.length) {
        throw new IllegalStateException(
          "Argument with id " + myIdx + " is not available, method " + methodDisplayName + " has only " + argumentTypes.length);
      }
      for (int i = 0; i < myIdx; i++) {
        index += argumentTypes[i].getSize();
      }
      mv.visitVarInsn(Opcodes.ALOAD, index);
    }
  }
}
