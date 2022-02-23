// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.debugger.agent;

import org.jetbrains.capture.org.objectweb.asm.*;
import org.jetbrains.capture.org.objectweb.asm.commons.LocalVariablesSorter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace", "rawtypes"})
public class CollectionBreakpointInstrumentor {
  private static final String CAPTURE_COLLECTION_MODIFICATION_METHOD_NAME = "captureCollectionModification";
  private static final String CAPTURE_COLLECTION_MODIFICATION_METHOD_DESC = "(ZZLjava/lang/Object;Ljava/lang/Object;Z)V";
  private static final String CAPTURE_COLLECTION_MODIFICATION_DEFAULT_METHOD_NAME = "captureCollectionModification";
  private static final String MULTISET_TYPE = "Lcom/intellij/rt/debugger/agent/CollectionBreakpointInstrumentor$Multiset;";
  private static final String CAPTURE_COLLECTION_MODIFICATION_DEFAULT_METHOD_DESC = "(" + MULTISET_TYPE + "Ljava/lang/Object;)V";
  private static final String CAPTURE_FIELD_MODIFICATION_METHOD_NAME = "captureFieldModification";
  private static final String CAPTURE_FIELD_MODIFICATION_METHOD_DESC = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Z)V";
  private static final String ON_CAPTURE_START_METHOD_NAME = "onCaptureStart";
  private static final String ON_CAPTURE_START_METHOD_DESC = "(Ljava/lang/Object;)Z";
  private static final String ON_CAPTURE_END_METHOD_NAME = "onCaptureEnd";
  private static final String ON_CAPTURE_END_METHOD_DESC = "(Ljava/lang/Object;)V";
  private static final String CAPTURE_COLLECTION_COPY_METHOD_NAME = "captureCollectionCopy";
  private static final String CAPTURE_COLLECTION_COPY_METHOD_DESC = "(ZLjava/lang/Object;)Lcom/intellij/rt/debugger/agent/CollectionBreakpointInstrumentor$Multiset;";
  private static final String CONSTRUCTOR_METHOD_NAME = "<init>";
  private static final String COLLECTION_INTERFACE_NAME = "java.util.Collection";
  private static final String MAP_INTERFACE_NAME = "java.util.Map";
  private static final String OBJECT_CLASS_NAME = "java.lang.Object";

  private static final ConcurrentIdentityHashMap myInstanceFilters = new ConcurrentIdentityHashMap();
  private static final ConcurrentHashMap<String, Set<String>> myFieldsToCapture = new ConcurrentHashMap<String, Set<String>>();
  private static final Set<String> myImmutableMethods;
  private static final Map<String, Boolean> mySpecialMethods;

  private static final Set<String> myUnprocessedNestedMembers = new HashSet<String>();
  private static final Set<String> myCollectionsToTransform = new HashSet<String>();
  private static final Set<String> myClassesToTransform = new HashSet<String>();
  private static final ReentrantLock myTransformLock = new ReentrantLock();

  public static boolean IMMUTABLE_METHODS_OPTIMIZATION_ENABLED = true;
  public static boolean SYNCHRONIZATION_ENABLED = false;

  private static Instrumentation ourInstrumentation;

  static {
    myImmutableMethods = new HashSet<String>();
    myImmutableMethods.add("size()I");
    myImmutableMethods.add("contains(Ljava/lang/Object;)Z");
    myImmutableMethods.add("iterator()Ljava/util/Iterator;");
    myImmutableMethods.add("isEmpty()Z");
    myImmutableMethods.add("toArray[Ljava/lang/Object;");
    myImmutableMethods.add("toArray([Ljava/lang/Object;)[Ljava/lang/Object;");
    myImmutableMethods.add("containsAll(Ljava/util/Collection;)Z");
    myImmutableMethods.add("equals([Ljava/lang/Object;)Z");
    myImmutableMethods.add("hashCode()I");
    myImmutableMethods.add("containsKey([Ljava/lang/Object;)Z");
    myImmutableMethods.add("containsValue([Ljava/lang/Object;)Z");
    myImmutableMethods.add("keySet()Ljava/util/Set;");
    myImmutableMethods.add("values()Ljava/util/Collection;");
    myImmutableMethods.add("entrySet()Ljava/util/Set;");

    mySpecialMethods = new HashMap<String, Boolean>();
    mySpecialMethods.put("add(Ljava/lang/Object;)Z", true);
    mySpecialMethods.put("remove(Ljava/lang/Object;)Z", false);
  }

  public static void init(Instrumentation instrumentation) {
    ourInstrumentation = instrumentation;
    ourInstrumentation.addTransformer(new CollectionTransformer(), true);
  }

  @SuppressWarnings("unused")
  public static void captureCollectionModification(boolean shouldCapture,
                                                   boolean modified,
                                                   Object collectionObj,
                                                   Object elem,
                                                   boolean isAddition) {
    try {
      if (!shouldCapture || !modified) {
        return;
      }
      CollectionBreakpointStorage.saveCollectionModification(collectionObj, elem, isAddition);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  @SuppressWarnings("unused")
  public static void captureCollectionModification(Multiset oldElements, Object newCollection) {
    try {
      CollectionInstanceState state = myInstanceFilters.get(newCollection);
      if (oldElements == null || state == null) {
        return;
      }
      ArrayList<Modification> modifications = getModifications(oldElements, newCollection);
      if (!modifications.isEmpty()) {
        saveCollectionModifications(newCollection, modifications);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  @SuppressWarnings("unused")
  public static boolean onCaptureStart(Object collectionObj) {
    try {
      CollectionInstanceState state = myInstanceFilters.get(collectionObj);
      if (state != null && !state.isBlocked()) {
        state.block();
        return true;
      }
      return false;
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return false;
  }

  @SuppressWarnings("unused")
  public static void onCaptureEnd(Object collectionObj) {
    try {
      CollectionInstanceState state = myInstanceFilters.get(collectionObj);
      if (state != null) {
        state.unlock();
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  @SuppressWarnings("unused")
  public static Multiset captureCollectionCopy(boolean shouldCapture, Object collectionObj) {
    try {
      if (!shouldCapture) {
        return null;
      }
      return Multiset.toMultiset(collectionObj);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  private static void saveCollectionModifications(Object collectionObj, ArrayList<Modification> modifications) {
    Collections.sort(modifications);
    for (Modification modification : modifications) {
      CollectionBreakpointStorage.saveCollectionModification(collectionObj,
                                                             modification.getElement(),
                                                             modification.isAddition());
    }
  }

  private static ArrayList<Modification> getModifications(Multiset oldElements, Object newCollection) {
    Multiset newElements = Multiset.toMultiset(newCollection);

    ArrayList<Modification> modifications = new ArrayList<Modification>();

    for (Map.Entry<Object, Integer> entry : newElements.entrySet()) {
      Integer newNumber = entry.getValue();
      Integer oldNumber = oldElements.get(entry.getKey());
      Object element = entry.getKey();
      if (element instanceof Element) {
        element = ((Element)element).getValue();
      }
      if (!newNumber.equals(oldNumber)) {
        boolean isAddition = oldNumber == null || newNumber > oldNumber;
        modifications.add(new Modification(element, isAddition));
      }
    }

    for (Map.Entry<Object, Integer> entry : oldElements.entrySet()) {
      Integer newNumber = newElements.get(entry.getKey());
      Object element = entry.getKey();
      if (element instanceof Element) {
        element = ((Element)element).getValue();
      }
      if (newNumber == null) {
        modifications.add(new Modification(element, false));
      }
    }

    return modifications;
  }

  @SuppressWarnings("unused")
  public static void captureFieldModification(Object collectionInstance,
                                              Object clsInstance,
                                              String clsTypeDesc,
                                              String fieldName,
                                              boolean shouldSaveStack) {
    try {
      if (collectionInstance == null) {
        return;
      }
      myInstanceFilters.add(collectionInstance);
      transformCollectionClassIfNeeded(collectionInstance.getClass());
      CollectionBreakpointStorage.saveFieldModification(clsTypeDesc, fieldName, clsInstance, collectionInstance, shouldSaveStack);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static void transformClassToCaptureFields(String qualifiedClsName) {
    try {
      myTransformLock.lock();
      for (Class cls : ourInstrumentation.getAllLoadedClasses()) {
        String name = cls.getName();
        if (name.equals(qualifiedClsName)) {
          try {
            ourInstrumentation.retransformClasses(cls);
          }
          catch (UnmodifiableClassException e) {
            e.printStackTrace();
          }
        }
      }
      transformNestedMembers(myClassesToTransform);
    }
    finally {
      myTransformLock.unlock();
    }
  }

  private static void transformNestedMembers(Set<String> container) {
    while (!myUnprocessedNestedMembers.isEmpty()) {
      container.addAll(myUnprocessedNestedMembers);
      Set<String> nestedNames = new HashSet<String>(myUnprocessedNestedMembers);
      myUnprocessedNestedMembers.clear();
      for (Class<?> loadedCls : ourInstrumentation.getAllLoadedClasses()) {
        String loadedClsName = getInternalClsName(loadedCls);
        if (nestedNames.contains(loadedClsName)) {
          try {
            ourInstrumentation.retransformClasses(loadedCls);
          }
          catch (UnmodifiableClassException e) {
            e.printStackTrace();
          }
        }
      }
    }
  }

  private static Set<String> getAllSuperClassesAndInterfacesNames(Class<?> cls) {
    Set<String> result = new HashSet<String>();
    Queue<Class<?>> supersQueue = new LinkedList<Class<?>>();
    supersQueue.add(cls);
    while (!supersQueue.isEmpty()) {
      Class<?> currentCls = supersQueue.poll();
      result.add(currentCls.getName());
      if (COLLECTION_INTERFACE_NAME.equals(currentCls.getName()) || MAP_INTERFACE_NAME.equals(currentCls.getName())) {
        continue;
      }
      Class<?> superCls = currentCls.getSuperclass();
      if (superCls != null && !OBJECT_CLASS_NAME.equals(superCls.getName()) && !result.contains(superCls.getName())) {
        supersQueue.add(superCls);
      }
      for (Class<?> inter : currentCls.getInterfaces()) {
        if (!result.contains(inter.getName())) {
          supersQueue.add(inter);
        }
      }
    }

    return result;
  }

  private static void transformCollectionClassIfNeeded(Class<?> cls) {
    try {
      myTransformLock.lock();
      String qualifiedClassName = getInternalClsName(cls);
      if (myCollectionsToTransform.contains(qualifiedClassName)) {
        return;
      }

      Set<String> allSupers = getAllSuperClassesAndInterfacesNames(cls);

      for (Class<?> loadedCls : ourInstrumentation.getAllLoadedClasses()) {
        if (allSupers.contains(loadedCls.getName())) {
          try {
            myCollectionsToTransform.add(getInternalClsName(loadedCls));
            ourInstrumentation.retransformClasses(loadedCls);
          }
          catch (UnmodifiableClassException e) {
            e.printStackTrace();
          }
        }
      }

      transformNestedMembers(myCollectionsToTransform);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    finally {
      myTransformLock.unlock();
    }
  }

  @SuppressWarnings("unused")
  public static void putFieldToCapture(String internalClsName, String fieldName) {
    myFieldsToCapture.putIfAbsent(internalClsName, Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>()));
    Set<String> fields = myFieldsToCapture.get(internalClsName);
    fields.add(fieldName);
    myClassesToTransform.add(internalClsName);
  }

  @SuppressWarnings("unused")
  public static void emulateFieldWatchpoint(String... clsTypesDesc) {
    for (String clsTypeDesc : clsTypesDesc) {
      transformClassToCaptureFields(getInternalClsName(clsTypeDesc));
    }
  }

  private static String getInstrumentorClassName() {
    return getInternalClsName(CollectionBreakpointInstrumentor.class);
  }

  public static String getInternalClsName(String typeDescriptor) {
    return Type.getType(typeDescriptor).getInternalName();
  }

  public static String getInternalClsName(Class<?> cls) {
    return Type.getInternalName(cls);
  }

  private static boolean isReturnInstruction(int opcode) {
    return opcode == Opcodes.IRETURN || opcode == Opcodes.FRETURN ||
           opcode == Opcodes.ARETURN || opcode == Opcodes.LRETURN ||
           opcode == Opcodes.DRETURN || opcode == Opcodes.RETURN;
  }

  private static boolean isMethodExit(int opcode) {
    return isReturnInstruction(opcode) || Opcodes.ATHROW == opcode;
  }

  private static class CollectionTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
      if (className != null && (myCollectionsToTransform.contains(className) || myClassesToTransform.contains(className))) {
        try {
          ClassReader reader = new ClassReader(classfileBuffer);
          ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);

          HashMap<String, Integer> exitPointsNumber = calculateExitPointsNumber(className, classfileBuffer);
          CollectionInstrumentor instrumentor = new CollectionInstrumentor(className, exitPointsNumber, Opcodes.API_VERSION, writer);
          reader.accept(instrumentor, ClassReader.EXPAND_FRAMES);
          byte[] bytes = writer.toByteArray();

          if (CaptureStorage.DEBUG) {
            try {
              System.out.println("instrumented: " + className);
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
          System.out.println("CollectionBreakpoint instrumentor: failed to instrument " + className);
          e.printStackTrace();
        }
      }
      return null;
    }

    private static HashMap<String, Integer> calculateExitPointsNumber(String className, byte[] classfileBuffer) {
      ClassReader reader = new ClassReader(classfileBuffer);
      ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
      CollectionMethodsVisitor instrumentor = new CollectionMethodsVisitor(className, Opcodes.API_VERSION, writer);
      reader.accept(instrumentor, 0);
      return instrumentor.getExitPointsNumber();
    }
  }

  static class CollectionInstrumentor extends ClassVisitor {

    private final HashMap<String, Integer> myExitPointsNumber;
    private final String myClsName;

    CollectionInstrumentor(String clsName, HashMap<String, Integer> exitPointsNumber, int api, ClassVisitor cv) {
      super(api, cv);
      myClsName = clsName;
      myExitPointsNumber = exitPointsNumber;
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
      if (!myCollectionsToTransform.contains(name) && !myClassesToTransform.contains(name)) {
        myUnprocessedNestedMembers.add(name); // save for processing after transform
      }
      super.visitInnerClass(name, outerName, innerName, access);
    }

    @Override
    public MethodVisitor visitMethod(final int access,
                                     final String name,
                                     final String desc,
                                     final String signature,
                                     final String[] exceptions) {
      MethodVisitor superMethodVisitor = super.visitMethod(access, name, desc, signature, exceptions);

      if ((access & Opcodes.ACC_BRIDGE) == 0 && name != null && desc != null) {
        if (myCollectionsToTransform.contains(myClsName) && access == Opcodes.ACC_PUBLIC && !name.equals(CONSTRUCTOR_METHOD_NAME)) {
          return new CollectionMethodVisitor(api, access, name, desc, superMethodVisitor);
        }
        else if (myClassesToTransform.contains(myClsName)) {
          return new CaptureFieldsMethodVisitor(api, superMethodVisitor);
        }
      }

      return superMethodVisitor;
    }

    private static void addCaptureFieldModificationCode(MethodVisitor mv,
                                                        String clsName,
                                                        String owner,
                                                        String fieldName,
                                                        boolean isStatic) {
      if (isStatic) {
        mv.visitInsn(Opcodes.DUP);
        mv.visitInsn(Opcodes.ACONST_NULL);
      }
      else if (clsName.equals(owner)) {
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
      }
      else {
        mv.visitInsn(Opcodes.DUP2);
        mv.visitInsn(Opcodes.SWAP);
      }
      mv.visitLdcInsn(owner);
      mv.visitLdcInsn(fieldName);
      mv.visitLdcInsn(true);
      mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                         getInstrumentorClassName(),
                         CAPTURE_FIELD_MODIFICATION_METHOD_NAME,
                         CAPTURE_FIELD_MODIFICATION_METHOD_DESC,
                         false);
    }

    private static void visitPutField(MethodVisitor mv, int opcode, String clsName, String owner, String fieldName) {
      Set<String> fieldNames = myFieldsToCapture.get(owner);
      boolean isPutOperation = opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC;
      if (isPutOperation && fieldNames != null && fieldNames.contains(fieldName)) {
        addCaptureFieldModificationCode(mv, clsName, owner, fieldName, opcode == Opcodes.PUTSTATIC);
      }
    }

    private static class TryCatchLabels {
      public final Label start;
      public final Label end;
      public final Label handler;

      private TryCatchLabels(Label start, Label end, Label handler) {
        this.start = start;
        this.end = end;
        this.handler = handler;
      }
    }

    private class CaptureFieldsMethodVisitor extends MethodVisitor {

      private CaptureFieldsMethodVisitor(int api, MethodVisitor methodVisitor) {
        super(api, methodVisitor);
      }

      @Override
      public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        visitPutField(mv, opcode, myClsName, owner, name);
        super.visitFieldInsn(opcode, owner, name, descriptor);
      }
    }

    private class CollectionMethodVisitor extends LocalVariablesSorter {

      private final String myMethodName;
      private final String myMethodDesc;
      private final ArrayList<TryCatchLabels> tryCatchLabels = new ArrayList<TryCatchLabels>();
      private final Label handler = new Label();
      private int collectionCopyVar;
      private int captureStartedVar;
      private int index = 0;

      protected CollectionMethodVisitor(int api, int access, String name, String descriptor, MethodVisitor methodVisitor) {
        super(api, access, descriptor, methodVisitor);
        myMethodName = name;
        myMethodDesc = descriptor;
      }

      private boolean isImmutable() {
        boolean isImmutable = myImmutableMethods.contains(myMethodName + myMethodDesc);
        return isImmutable && IMMUTABLE_METHODS_OPTIMIZATION_ENABLED;
      }

      @Override
      public void visitCode() {
        if (isImmutable()) {
          super.visitCode();
          return;
        }

        super.visitCode();

        Integer exitPoints = myExitPointsNumber.get(myMethodName + myMethodDesc);
        if (exitPoints != null && exitPoints > 0) {
          for (int i = 0; i < exitPoints; i++) {
            tryCatchLabels.add(new TryCatchLabels(new Label(), new Label(), handler));
            mv.visitTryCatchBlock(tryCatchLabels.get(i).start, tryCatchLabels.get(i).end, handler, null);
          }
          mv.visitLabel(tryCatchLabels.get(0).start);
        }

        addStartCaptureCode();

        boolean isSpecial = mySpecialMethods.containsKey(myMethodName + myMethodDesc);
        if (!isSpecial) {
          addCaptureCollectionCopyCode();
        }
      }

      private void addEndCaptureCode() {
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                           getInstrumentorClassName(),
                           ON_CAPTURE_END_METHOD_NAME,
                           ON_CAPTURE_END_METHOD_DESC,
                           false);
      }

      private void addStartCaptureCode() {
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                           getInstrumentorClassName(),
                           ON_CAPTURE_START_METHOD_NAME,
                           ON_CAPTURE_START_METHOD_DESC,
                           false);
        captureStartedVar = newLocal(Type.BOOLEAN_TYPE);
        mv.visitVarInsn(Opcodes.ISTORE, captureStartedVar);
      }

      private void addCaptureCollectionCopyCode() {
        mv.visitVarInsn(Opcodes.ILOAD, captureStartedVar);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                           getInstrumentorClassName(),
                           CAPTURE_COLLECTION_COPY_METHOD_NAME,
                           CAPTURE_COLLECTION_COPY_METHOD_DESC,
                           false);
        collectionCopyVar = newLocal(Type.getType(MULTISET_TYPE));
        mv.visitVarInsn(Opcodes.ASTORE, collectionCopyVar);
      }

      private void addCaptureCollectionModificationDefaultCode() {
        mv.visitVarInsn(Opcodes.ALOAD, collectionCopyVar);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                           getInstrumentorClassName(),
                           CAPTURE_COLLECTION_MODIFICATION_DEFAULT_METHOD_NAME,
                           CAPTURE_COLLECTION_MODIFICATION_DEFAULT_METHOD_DESC,
                           false);
      }

      private void addCaptureCollectionModificationCode() {
        String method = myMethodName + myMethodDesc;
        if (isImmutable()) {
          return;
        }

        Boolean isAddition = mySpecialMethods.get(method);
        if (isAddition != null) {
          addCaptureSimpleModificationCode(isAddition);
        }
        else {
          addCaptureCollectionModificationDefaultCode();
        }
      }

      private void addCaptureSimpleModificationCode(boolean isAddition) {
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ILOAD, captureStartedVar);
        mv.visitInsn(Opcodes.SWAP);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitLdcInsn(isAddition);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                           getInstrumentorClassName(),
                           CAPTURE_COLLECTION_MODIFICATION_METHOD_NAME,
                           CAPTURE_COLLECTION_MODIFICATION_METHOD_DESC,
                           false);
      }

      @Override
      public void visitInsn(int opcode) {
        if (isImmutable()) {
          super.visitInsn(opcode);
          return;
        }

        if (isReturnInstruction(opcode)) {
          addCaptureCollectionModificationCode();
          addEndCaptureCode();
          processExit(opcode);
        }
        else {
          super.visitInsn(opcode);
        }
      }

      @Override
      public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        visitPutField(mv, opcode, myClsName, owner, name);
        super.visitFieldInsn(opcode, owner, name, descriptor);
      }

      private void processExit(int opcode) {
        mv.visitLabel(tryCatchLabels.get(index).end);
        super.visitInsn(opcode);
        index++;
        Integer exitPoints = myExitPointsNumber.get(myMethodName + myMethodDesc);
        if (exitPoints.equals(index)) {
          mv.visitLabel(handler);
          mv.visitFrame(Opcodes.F_SAME1, 0, null, 0, null);
          addEndCaptureCode();
          mv.visitInsn(Opcodes.ATHROW);
        }
        else {
          mv.visitLabel(tryCatchLabels.get(index).start);
        }
      }
    }
  }

  private static class CollectionMethodsVisitor extends ClassVisitor {

    private final String myClsName;
    private final HashMap<String, Integer> myExitPointsNumber = new HashMap<String, Integer>();

    CollectionMethodsVisitor(String clsName, int api, ClassVisitor cv) {
      super(api, cv);
      myClsName = clsName;
    }

    private HashMap<String, Integer> getExitPointsNumber() {
      return myExitPointsNumber;
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String desc, String signature, final String[] exceptions) {
      if ((access & Opcodes.ACC_BRIDGE) == 0 && name != null && desc != null) {
        if (myCollectionsToTransform.contains(myClsName) &&
            access == Opcodes.ACC_PUBLIC && !name.equals(CONSTRUCTOR_METHOD_NAME)) {
          return new MethodVisitor(api, super.visitMethod(access, name, desc, signature, exceptions)) {
            @Override
            public void visitInsn(int opcode) {
              if (isReturnInstruction(opcode)) {
                Integer value = myExitPointsNumber.get(name + desc);
                if (value == null) {
                  value = 0;
                }
                myExitPointsNumber.put(name + desc, value + 1);
              }
              super.visitInsn(opcode);
            }
          };
        }
      }
      return super.visitMethod(access, name, desc, signature, exceptions);
    }
  }

  private static class Modification implements Comparable<Modification> {
    private final Object myElement;
    private final boolean myIsAddition;

    private Modification(Object element, boolean isAddition) {
      myElement = element;
      myIsAddition = isAddition;
    }

    Object getElement() {
      return myElement;
    }

    boolean isAddition() {
      return myIsAddition;
    }

    @Override
    public int compareTo(Modification o) {
      if (myIsAddition == o.isAddition()) {
        return 0;
      }
      return myIsAddition ? 1 : -1;
    }
  }

  private static class Multiset {
    private final HashMap<Object, Integer> myContainer = new HashMap<Object, Integer>();

    public void add(Object element) {
      Integer number = myContainer.get(element);
      if (number == null) {
        myContainer.put(element, 1);
      }
      else {
        myContainer.put(element, number + 1);
      }
    }

    public Integer get(Object elem) {
      return myContainer.get(elem);
    }

    public void remove(Object element) {
      Integer number = myContainer.get(element);
      if (number == null) {
        return;
      }
      if (number == 1) {
        myContainer.remove(element);
      }
      else {
        myContainer.put(element, number - 1);
      }
    }

    private Set<Map.Entry<Object, Integer>> entrySet() {
      return myContainer.entrySet();
    }

    public static Multiset toMultiset(Object collection) {
      Multiset multiset = new Multiset();
      if (collection instanceof Collection) {
        for (Object element : (Collection<?>)collection) {
          multiset.add(new Element(element));
        }
      }
      else if (collection instanceof Map) {
        for (Map.Entry<?, ?> element : ((Map<?, ?>)collection).entrySet()) {
          multiset.add(new Pair(element.getKey(), element.getValue()));
        }
      }
      return multiset;
    }
  }

  private static class ConcurrentIdentityHashMap {
    private final Map<Object, CollectionInstanceState> myContainer = new IdentityHashMap<Object, CollectionInstanceState>();
    private final ReentrantLock myLock = new ReentrantLock();

    public void add(Object obj) {
      myLock.lock();
      try {
        if (!myContainer.containsKey(obj)) {
          myContainer.put(obj, new CollectionInstanceState());
        }
      }
      finally {
        myLock.unlock();
      }
    }

    public CollectionInstanceState get(Object obj) {
      myLock.lock();
      try {
        return myContainer.get(obj);
      }
      finally {
        myLock.unlock();
      }
    }

    public boolean contains(Object obj) {
      myLock.lock();
      try {
        return myContainer.containsKey(obj);
      }
      finally {
        myLock.unlock();
      }
    }
  }

  public static class CollectionInstanceState {
    private final ReentrantLock myLock = new ReentrantLock();
    private volatile boolean myMethodIsCalledNow = false;

    public void block() {
      if (SYNCHRONIZATION_ENABLED) {
        myLock.lock();
      }
      myMethodIsCalledNow = true;
    }

    public boolean isBlocked() {
      return myMethodIsCalledNow;
    }

    public void unlock() {
      try {
        myMethodIsCalledNow = false;
        if (SYNCHRONIZATION_ENABLED) {
          myLock.unlock();
        }
      }
      catch (Throwable e) {
        e.printStackTrace();
      }
    }
  }

  private static class Element {
    private final Object value;

    private Element(Object value) {
      this.value = value;
    }

    private Object getValue() {
      return value;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Element &&
             value == ((Element)obj).value;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(value);
    }
  }

  private static class Pair implements Map.Entry<Object, Object> {
    private final Object key;
    private final Object value;

    private Pair(Object key, Object value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public Object getKey() {
      return key;
    }

    @Override
    public Object getValue() {
      return value;
    }

    @Override
    public Object setValue(Object value) {
      return null;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Pair &&
             ((Pair)obj).key == key &&
             ((Pair)obj).value == value;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(key) + 31 * System.identityHashCode(key);
    }
  }
}