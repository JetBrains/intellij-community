// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.debugger.agent;

import org.jetbrains.capture.org.objectweb.asm.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.net.URI;
import java.security.ProtectionDomain;
import java.util.*;

/**
 * @author egor
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public class CaptureAgent {
  private static Instrumentation ourInstrumentation;
  private static boolean DEBUG = false;

  private static Map<String, List<InstrumentPoint>> myCapturePoints = new HashMap<String, List<InstrumentPoint>>();
  private static final Map<String, List<InstrumentPoint>> myInsertPoints = new HashMap<String, List<InstrumentPoint>>();

  public static void premain(String args, Instrumentation instrumentation) {
    ourInstrumentation = instrumentation;
    try {
      readSettings(args);

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

  private static void readSettings(String uri) {
    if (uri == null || uri.isEmpty()) {
      return;
    }

    Properties properties = new Properties();
    File file;
    try {
      FileReader reader = null;
      try {
        file = new File(new URI(uri));
        reader = new FileReader(file);
        properties.load(reader);
      }
      finally {
        if (reader != null) {
          reader.close();
        }
      }
    }
    catch (Exception e) {
      System.out.println("Capture agent: unable to read settings");
      e.printStackTrace();
      return;
    }

    DEBUG = Boolean.parseBoolean(properties.getProperty("debug", "false"));
    if (DEBUG) {
      CaptureStorage.setDebug(true);
    }

    if (Boolean.parseBoolean(properties.getProperty("disabled", "false"))) {
      CaptureStorage.setEnabled(false);
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
    if (Boolean.parseBoolean(properties.getProperty("deleteSettings", "true"))) {
      //noinspection ResultOfMethodCallIgnored
      file.delete();
    }
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
        List<InstrumentPoint> capturePoints = getNotNull(myCapturePoints.get(className));
        List<InstrumentPoint> insertPoints = getNotNull(myInsertPoints.get(className));
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
            System.out.println("Capture agent: failed to instrument " + className);
            e.printStackTrace();
          }
        }
      }
      return null;
    }
  }

  private static class CaptureInstrumentor extends ClassVisitor {
    private final List<InstrumentPoint> myCapturePoints;
    private final List<InstrumentPoint> myInsertPoints;
    private final Map<String, String> myFields = new HashMap<String, String>();
    private String mySuperName;

    public CaptureInstrumentor(int api, ClassVisitor cv, List<InstrumentPoint> capturePoints, List<InstrumentPoint> insertPoints) {
      super(api, cv);
      this.myCapturePoints = capturePoints;
      this.myInsertPoints = insertPoints;
    }

    private static String getNewName(String name) {
      return name + CaptureStorage.GENERATED_INSERT_METHOD_POSTFIX;
    }

    private static String getMethodDisplayName(String className, String methodName, String desc) {
      return className + "." + methodName + desc;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
      mySuperName = superName;
      super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
      myFields.put(name, desc);
      return super.visitField(access, name, desc, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(final int access, String name, final String desc, String signature, String[] exceptions) {
      if ((access & Opcodes.ACC_BRIDGE) == 0) {
        for (final InstrumentPoint capturePoint : myCapturePoints) {
          if (capturePoint.matchesMethod(name, desc)) {
            final String methodDisplayName = getMethodDisplayName(capturePoint.myClassName, name, desc);
            if (DEBUG) {
              System.out.println("Capture agent: instrumented capture point at " + methodDisplayName);
            }
            // for constructors and "this" key - move capture to after the super constructor call
            if ("<init>".equals(name) && capturePoint.myKeyProvider == THIS_KEY_PROVIDER) {
              return new MethodVisitor(api, super.visitMethod(access, name, desc, signature, exceptions)) {
                boolean captured = false;

                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                  super.visitMethodInsn(opcode, owner, name, desc, itf);
                  if (opcode == Opcodes.INVOKESPECIAL && !captured && owner.equals(mySuperName) && name.equals("<init>")) { // super constructor
                    capture(mv, capturePoint.myKeyProvider, (access & Opcodes.ACC_STATIC) != 0,
                            Type.getMethodType(desc).getArgumentTypes(), methodDisplayName);
                    captured = true;
                  }
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

        for (InstrumentPoint insertPoint : myInsertPoints) {
          if (insertPoint.matchesMethod(name, desc)) {
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
                                 InstrumentPoint insertPoint,
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

    private void capture(MethodVisitor mv,
                         KeyProvider keyProvider,
                         boolean isStatic,
                         Type[] argumentTypes,
                         String methodDisplayName) {
      storageCall(mv, keyProvider, isStatic, argumentTypes, "capture", methodDisplayName);
    }

    private void insertEnter(MethodVisitor mv,
                             KeyProvider keyProvider,
                             boolean isStatic,
                             Type[] argumentTypes,
                             String methodDisplayName) {
      storageCall(mv, keyProvider, isStatic, argumentTypes, "insertEnter", methodDisplayName);
    }

    private void insertExit(MethodVisitor mv,
                            KeyProvider keyProvider,
                            boolean isStatic,
                            Type[] argumentTypes,
                            String methodDisplayName) {
      storageCall(mv, keyProvider, isStatic, argumentTypes, "insertExit", methodDisplayName);
    }

    private void storageCall(MethodVisitor mv,
                             KeyProvider keyProvider,
                             boolean isStatic,
                             Type[] argumentTypes,
                             String storageMethodName,
                             String methodDisplayName) {
      keyProvider.loadKey(mv, isStatic, argumentTypes, methodDisplayName, this);
      mv.visitMethodInsn(Opcodes.INVOKESTATIC, CaptureStorage.class.getName().replaceAll("\\.", "/"), storageMethodName,
                         "(Ljava/lang/Object;)V", false);
    }
  }

  private static class InstrumentPoint {
    final static String ANY_DESC = "*";

    final String myClassName;
    final String myMethodName;
    final String myMethodDesc;
    final KeyProvider myKeyProvider;

    public InstrumentPoint(String className, String methodName, String methodDesc, KeyProvider keyProvider) {
      myClassName = className;
      myMethodName = methodName;
      myMethodDesc = methodDesc;
      myKeyProvider = keyProvider;
    }

    boolean matchesMethod(String name, String desc) {
      if (!myMethodName.equals(name)) {
        return false;
      }
      return myMethodDesc.equals(ANY_DESC) || myMethodDesc.equals(desc);
    }
  }

  // to be run from the debugger
  @SuppressWarnings("unused")
  public static void setCapturePoints(Object[][] capturePoints) throws UnmodifiableClassException {
    Set<String> classNames = new HashSet<String>(myCapturePoints.keySet());

    Map<String, List<InstrumentPoint>> points = new HashMap<String, List<InstrumentPoint>>();
    for (Object[] capturePoint : capturePoints) {
      String className = (String)capturePoint[0];
      classNames.add(className);
      List<InstrumentPoint> currentPoints = points.get(className);
      if (currentPoints == null) {
        currentPoints = new ArrayList<InstrumentPoint>();
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
    KeyProvider keyProvider = createKeyProvider(Arrays.copyOfRange(split, 3, split.length));
    addCapturePoint(capture, split[0], split[1], split[2], keyProvider);
  }

  private static void addCapturePoint(boolean capture, String className, String methodName, String methodDesc, KeyProvider keyProvider) {
    Map<String, List<InstrumentPoint>> map = capture ? myCapturePoints : myInsertPoints;
    List<InstrumentPoint> points = map.get(className);
    if (points == null) {
      points = new ArrayList<InstrumentPoint>(1);
      map.put(className, points);
    }
    points.add(new InstrumentPoint(className, methodName, methodDesc, keyProvider));
  }

  private static final KeyProvider FIRST_PARAM = param(0);

  static final KeyProvider THIS_KEY_PROVIDER = new KeyProvider() {
    @Override
    public void loadKey(MethodVisitor mv,
                        boolean isStatic,
                        Type[] argumentTypes,
                        String methodDisplayName,
                        CaptureInstrumentor instrumentor) {
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
    return new FieldKeyProvider(line[0], line[1]);
  }

  private static boolean isNumber(String s) {
    if (s == null) return false;
    for (int i = 0; i < s.length(); ++i) {
      if (!Character.isDigit(s.charAt(i))) return false;
    }
    return true;
  }

  private interface KeyProvider {
    void loadKey(MethodVisitor mv, boolean isStatic, Type[] argumentTypes, String methodDisplayName, CaptureInstrumentor instrumentor);
  }

  private static class FieldKeyProvider implements KeyProvider {
    private final String myClassName;
    private final String myFieldName;

    public FieldKeyProvider(String className, String fieldName) {
      myClassName = className;
      myFieldName = fieldName;
    }

    @Override
    public void loadKey(MethodVisitor mv,
                        boolean isStatic,
                        Type[] argumentTypes,
                        String methodDisplayName,
                        CaptureInstrumentor instrumentor) {
      String desc = instrumentor.myFields.get(myFieldName);
      if (desc == null) {
        throw new IllegalStateException("Field " + myFieldName + " was not found");
      }
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitFieldInsn(Opcodes.GETFIELD, myClassName, myFieldName, desc);
    }
  }

  private static class ParamKeyProvider implements KeyProvider {
    private final int myIdx;

    public ParamKeyProvider(int idx) {
      myIdx = idx;
    }

    @Override
    public void loadKey(MethodVisitor mv,
                        boolean isStatic,
                        Type[] argumentTypes,
                        String methodDisplayName,
                        CaptureInstrumentor instrumentor) {
      int index = isStatic ? 0 : 1;
      if (myIdx >= argumentTypes.length) {
        throw new IllegalStateException(
          "Argument with id " + myIdx + " is not available, method " + methodDisplayName + " has only " + argumentTypes.length);
      }
      int sort = argumentTypes[myIdx].getSort();
      if (sort != Type.OBJECT && sort != Type.ARRAY) {
        throw new IllegalStateException(
          "Argument with id " + myIdx + " in method " + methodDisplayName + " must be an object");
      }
      for (int i = 0; i < myIdx; i++) {
        index += argumentTypes[i].getSize();
      }
      mv.visitVarInsn(Opcodes.ALOAD, index);
    }
  }

  private static void addCapture(String className, String methodName, KeyProvider key) {
    addCapturePoint(true, className, methodName, InstrumentPoint.ANY_DESC, key);
  }

  private static void addInsert(String className, String methodName, KeyProvider key) {
    addCapturePoint(false, className, methodName, InstrumentPoint.ANY_DESC, key);
  }

  private static KeyProvider param(int idx) {
    return new ParamKeyProvider(idx);
  }

  // predefined points
  static {
    addCapture("java/awt/event/InvocationEvent", "<init>", THIS_KEY_PROVIDER);
    addInsert("java/awt/event/InvocationEvent", "dispatch", THIS_KEY_PROVIDER);

    addCapture("java/lang/Thread", "start", THIS_KEY_PROVIDER);
    addInsert("java/lang/Thread", "run", THIS_KEY_PROVIDER);

    addCapture("java/util/concurrent/FutureTask", "<init>", THIS_KEY_PROVIDER);
    addInsert("java/util/concurrent/FutureTask", "run", THIS_KEY_PROVIDER);
    addInsert("java/util/concurrent/FutureTask", "runAndReset", THIS_KEY_PROVIDER);

    addCapture("java/util/concurrent/CompletableFuture$AsyncSupply", "<init>", THIS_KEY_PROVIDER);
    addInsert("java/util/concurrent/CompletableFuture$AsyncSupply", "run", THIS_KEY_PROVIDER);

    addCapture("java/util/concurrent/CompletableFuture$AsyncRun", "<init>", THIS_KEY_PROVIDER);
    addInsert("java/util/concurrent/CompletableFuture$AsyncRun", "run", THIS_KEY_PROVIDER);

    addCapture("java/util/concurrent/CompletableFuture$UniAccept", "<init>", THIS_KEY_PROVIDER);
    addInsert("java/util/concurrent/CompletableFuture$UniAccept", "tryFire", THIS_KEY_PROVIDER);

    addCapture("java/util/concurrent/CompletableFuture$UniRun", "<init>", THIS_KEY_PROVIDER);
    addInsert("java/util/concurrent/CompletableFuture$UniRun", "tryFire", THIS_KEY_PROVIDER);

    // netty
    addCapture("io/netty/util/concurrent/SingleThreadEventExecutor", "addTask", FIRST_PARAM);
    addInsert("io/netty/util/concurrent/AbstractEventExecutor", "safeExecute", FIRST_PARAM);

    // scala
    addCapture("scala/concurrent/impl/Future$PromiseCompletingRunnable", "<init>", THIS_KEY_PROVIDER);
    addInsert("scala/concurrent/impl/Future$PromiseCompletingRunnable", "run", THIS_KEY_PROVIDER);

    addCapture("scala/concurrent/impl/CallbackRunnable", "<init>", THIS_KEY_PROVIDER);
    addInsert("scala/concurrent/impl/CallbackRunnable", "run", THIS_KEY_PROVIDER);

    // akka-scala
    addCapture("akka/actor/ScalaActorRef", "$bang", FIRST_PARAM);
    addCapture("akka/actor/RepointableActorRef", "$bang", FIRST_PARAM);
    addCapture("akka/actor/LocalActorRef", "$bang", FIRST_PARAM);
    addInsert("akka/actor/Actor$class", "aroundReceive", param(2));

    // JavaFX
    addCapture("com/sun/glass/ui/InvokeLaterDispatcher", "invokeLater", FIRST_PARAM);
    addInsert("com/sun/glass/ui/InvokeLaterDispatcher$Future", "run",
              new FieldKeyProvider("com/sun/glass/ui/InvokeLaterDispatcher$Future", "runnable"));
  }
}
