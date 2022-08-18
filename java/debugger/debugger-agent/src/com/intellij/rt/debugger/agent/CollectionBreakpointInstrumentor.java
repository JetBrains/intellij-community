// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.debugger.agent;

import org.jetbrains.capture.org.objectweb.asm.*;
import org.jetbrains.capture.org.objectweb.asm.commons.LocalVariablesSorter;
import org.jetbrains.capture.org.objectweb.asm.tree.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace", "rawtypes"})
public class CollectionBreakpointInstrumentor {
  private static final String OBJECT_TYPE = "Ljava/lang/Object;";
  private static final String STRING_TYPE = "Ljava/lang/String;";
  private static final String MULTISET_TYPE = "Lcom/intellij/rt/debugger/agent/CollectionBreakpointInstrumentor$Multiset;";
  private static final String PAIR_TYPE = "Lcom/intellij/rt/debugger/agent/CollectionBreakpointInstrumentor$Pair;";
  private static final String COLLECTION_TYPE = "java/util/Collection";
  private static final String MAP_TYPE = "java/util/Map";
  private static final String ABSTRACT_COLLECTION_TYPE = "java/util/AbstractCollection";
  private static final String ABSTRACT_LIST_TYPE = "java/util/AbstractList";
  private static final String ARRAY_LIST_TYPE = "java/util/ArrayList";
  private static final String CAPTURE_COLLECTION_MODIFICATION_METHOD_NAME = "captureCollectionModification";
  private static final String CAPTURE_COLLECTION_MODIFICATION_METHOD_DESC = "(" + "Z" + "Z" + OBJECT_TYPE + OBJECT_TYPE + "Z" + ")V";
  private static final String CAPTURE_COLLECTION_MODIFICATION_DEFAULT_METHOD_NAME = "captureCollectionModification";
  private static final String CAPTURE_COLLECTION_MODIFICATION_DEFAULT_METHOD_DESC = "(" + MULTISET_TYPE + OBJECT_TYPE + ")V";
  private static final String CAPTURE_FIELD_MODIFICATION_METHOD_NAME = "captureFieldModification";
  private static final String CAPTURE_FIELD_MODIFICATION_METHOD_DESC = "(" + OBJECT_TYPE + OBJECT_TYPE + STRING_TYPE + STRING_TYPE + "Z)V";
  private static final String ON_CAPTURE_START_METHOD_NAME = "onCaptureStart";
  private static final String ON_CAPTURE_START_METHOD_DESC = "(" + OBJECT_TYPE + "Z)Z";
  private static final String ON_CAPTURE_END_METHOD_NAME = "onCaptureEnd";
  private static final String ON_CAPTURE_END_METHOD_DESC = "(" + OBJECT_TYPE + "Z)V";
  private static final String CAPTURE_COLLECTION_COPY_METHOD_NAME = "captureCollectionCopy";
  private static final String CAPTURE_COLLECTION_COPY_METHOD_DESC = "(" + "Z" + OBJECT_TYPE + ")" + MULTISET_TYPE;
  private static final String CONSTRUCTOR_METHOD_NAME = "<init>";
  private static final String CREATE_PAIR_METHOD_NAME = "createPair";
  private static final String CREATE_PAIR_METHOD_DESC = "(" + OBJECT_TYPE + OBJECT_TYPE + ")" + PAIR_TYPE;
  private static final String COLLECTION_INTERFACE_NAME = "java.util.Collection";
  private static final String MAP_INTERFACE_NAME = "java.util.Map";
  private static final String JAVA_UTIL_PACKAGE_NAME = "java.util";
  private static final String OBJECT_CLASS_NAME = "java.lang.Object";

  private static final ConcurrentIdentityHashMap myInstanceFilters = new ConcurrentIdentityHashMap();
  private static final ConcurrentHashMap<String, Set<String>> myFieldsToCapture = new ConcurrentHashMap<String, Set<String>>();

  private static final Map<String, KnownMethodsSet> myKnownMethods = new HashMap<String, KnownMethodsSet>();

  private static final Set<String> myUnprocessedNestedMembers = new HashSet<String>();
  private static final Map<String, KnownMethodsSet> myCollectionsToTransform = new HashMap<String, KnownMethodsSet>();
  private static final Set<String> myClassesToTransform = new HashSet<String>();
  private static final ReentrantLock myTransformLock = new ReentrantLock();

  @SuppressWarnings("StaticNonFinalField")
  public static boolean DEBUG; // set form debugger

  private static Instrumentation ourInstrumentation;

  static {
    KnownMethodsSet collectionKnownMethods = new KnownMethodsSet();
    collectionKnownMethods.add(new ImmutableMethod("size()I"));
    collectionKnownMethods.add(new ImmutableMethod("contains(Ljava/lang/Object;)Z"));
    collectionKnownMethods.add(new ImmutableMethod("iterator()Ljava/util/Iterator;"));
    collectionKnownMethods.add(new ImmutableMethod("toArray()[Ljava/lang/Object;"));
    collectionKnownMethods.add(new ImmutableMethod("toArray([Ljava/lang/Object;)[Ljava/lang/Object;"));
    collectionKnownMethods.add(new ImmutableMethod("containsAll(Ljava/util/Collection;)Z"));
    collectionKnownMethods.add(new ImmutableMethod("toArray(Ljava/util/function/IntFunction;)[Ljava/lang/Object;"));
    collectionKnownMethods.add(new ImmutableMethod("spliterator()Ljava/util/Spliterator;"));
    collectionKnownMethods.add(new ImmutableMethod("parallelStream()Ljava/util/stream/Stream;"));
    collectionKnownMethods.add(new ImmutableMethod("equals(Ljava/lang/Object;)Z"));
    collectionKnownMethods.add(new ImmutableMethod("hashCode()I"));
    collectionKnownMethods.add(new ReturnsBooleanMethod("add(Ljava/lang/Object;)Z", true));
    collectionKnownMethods.add(new ReturnsBooleanMethod("remove(Ljava/lang/Object;)Z", false));
    myKnownMethods.put(COLLECTION_TYPE, collectionKnownMethods);

    KnownMethodsSet abstractCollectionKnownMethods = new KnownMethodsSet();
    abstractCollectionKnownMethods.add(new ImmutableMethod("toString()Ljava/lang/String;"));
    myKnownMethods.put(ABSTRACT_COLLECTION_TYPE, abstractCollectionKnownMethods);

    KnownMethodsSet abstractListKnownMethods = new KnownMethodsSet();
    abstractListKnownMethods.add(new ImmutableMethod("indexOf(Ljava/lang/Object;)I"));
    abstractListKnownMethods.add(new ImmutableMethod("lastIndexOf(Ljava/lang/Object;)I"));
    abstractListKnownMethods.add(new ImmutableMethod("listIterator()Ljava/util/ListIterator;"));
    abstractListKnownMethods.add(new ImmutableMethod("listIterator(I)Ljava/util/ListIterator;"));
    abstractListKnownMethods.add(new ImmutableMethod("subList(II)Ljava/util/List;"));
    myKnownMethods.put(ABSTRACT_LIST_TYPE, abstractListKnownMethods);

    KnownMethodsSet arrayListKnownMethods = new KnownMethodsSet();
    arrayListKnownMethods.add(new ImmutableMethod("indexOfRange(Ljava/lang/Object;II)I"));
    arrayListKnownMethods.add(new ImmutableMethod("lastIndexOfRange(Ljava/lang/Object;II)I"));
    arrayListKnownMethods.add(new ImmutableMethod("clone()Ljava/lang/Object;"));
    arrayListKnownMethods.add(new ImmutableMethod("equalsRange(Ljava/util/List;II)Z"));
    arrayListKnownMethods.add(new ImmutableMethod("equalsArrayList(Ljava/util/ArrayList;)Z"));
    arrayListKnownMethods.add(new ImmutableMethod("hashCodeRange(II)I"));
    arrayListKnownMethods.add(new ImmutableMethod("outOfBoundsMsg(I)Ljava/lang/String;"));
    myKnownMethods.put(ARRAY_LIST_TYPE, arrayListKnownMethods);

    KnownMethodsSet mapKnownMethods = new KnownMethodsSet();
    mapKnownMethods.add(new ImmutableMethod("size()I"));
    mapKnownMethods.add(new ImmutableMethod("isEmpty()Z"));
    mapKnownMethods.add(new ImmutableMethod("keySet()Ljava/util/Set;"));
    mapKnownMethods.add(new ImmutableMethod("values()Ljava/util/Collection;"));
    mapKnownMethods.add(new ImmutableMethod("entrySet()Ljava/util/Set;"));
    mapKnownMethods.add(new ImmutableMethod("containsKey(Ljava/lang/Object;)Z"));
    mapKnownMethods.add(new ImmutableMethod("containsValue(Ljava/lang/Object;)Z"));
    mapKnownMethods.add(new ImmutableMethod("equals(Ljava/lang/Object;)Z"));
    mapKnownMethods.add(new ImmutableMethod("hashCode()I"));
    mapKnownMethods.add(new PutMethod());
    mapKnownMethods.add(new RemoveKeyMethod());
    myKnownMethods.put(MAP_TYPE, mapKnownMethods);
  }

  public static void init(Instrumentation instrumentation) {
    ourInstrumentation = instrumentation;
    ourInstrumentation.addTransformer(new CollectionBreakpointTransformer(), true);

    if (DEBUG) {
      System.out.println("Collection breakpoint instrumentor: ready");
    }
  }

  private static void processFailedToInstrumentError(String className, Exception error) {
    System.out.println("CollectionBreakpoint instrumentor: failed to instrument " + className);
    error.printStackTrace();
  }

  private static void writeDebugInfo(String className, byte[] bytes) {
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

  @SuppressWarnings("unused")
  public static void captureCollectionModification(boolean shouldCapture,
                                                   boolean modified,
                                                   Object collectionInstance,
                                                   Object elem,
                                                   boolean isAddition) {
    try {
      if (!shouldCapture || !modified) {
        return;
      }
      CollectionBreakpointStorage.saveCollectionModification(collectionInstance, elem, isAddition);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  @SuppressWarnings("unused")
  public static void captureCollectionModification(Multiset oldElements, Object newCollectionInstance) {
    try {
      CollectionInstanceLock lock = myInstanceFilters.get(newCollectionInstance);
      if (oldElements == null || lock == null) {
        return;
      }
      ArrayList<Modification> modifications = getModifications(oldElements, newCollectionInstance);
      if (!modifications.isEmpty()) {
        saveCollectionModifications(newCollectionInstance, modifications);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  @SuppressWarnings("unused")
  public static boolean onCaptureStart(Object collectionInstance, boolean shouldSynchronized) {
    try {
      CollectionInstanceLock lock = myInstanceFilters.get(collectionInstance);
      if (lock != null) {
        return lock.lock(shouldSynchronized);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return false;
  }

  @SuppressWarnings("unused")
  public static void onCaptureEnd(Object collectionInstance, boolean shouldSynchronized) {
    try {
      CollectionInstanceLock lock = myInstanceFilters.get(collectionInstance);
      if (lock != null) {
        lock.unlock(shouldSynchronized);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  @SuppressWarnings("unused")
  public static Multiset captureCollectionCopy(boolean shouldCapture, Object collectionInstance) {
    try {
      if (!shouldCapture) {
        return null;
      }
      return Multiset.toMultiset(collectionInstance);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  private static void saveCollectionModifications(Object collectionInstance, ArrayList<Modification> modifications) {
    Collections.sort(modifications);
    for (Modification modification : modifications) {
      CollectionBreakpointStorage.saveCollectionModification(collectionInstance,
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
      if (element instanceof Wrapper) {
        element = ((Wrapper)element).getValue();
      }
      if (!newNumber.equals(oldNumber)) {
        boolean isAddition = oldNumber == null || newNumber > oldNumber;
        modifications.add(new Modification(element, isAddition));
      }
    }

    for (Map.Entry<Object, Integer> entry : oldElements.entrySet()) {
      Integer newNumber = newElements.get(entry.getKey());
      Object element = entry.getKey();
      if (element instanceof Wrapper) {
        element = ((Wrapper)element).getValue();
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
      transformClassNestedMembers();
    }
    finally {
      myTransformLock.unlock();
    }
  }

  private static void transformClassNestedMembers() {
    while (!myUnprocessedNestedMembers.isEmpty()) {
      myClassesToTransform.addAll(myUnprocessedNestedMembers);
      Set<String> nestedNames = new HashSet<String>(myUnprocessedNestedMembers);
      myUnprocessedNestedMembers.clear();
      transformNestedMembers(nestedNames);
    }
  }

  private static void transformCollectionNestedMembers() {
    for (String nestedName : myUnprocessedNestedMembers) {
      myCollectionsToTransform.put(nestedName, new KnownMethodsSet());
    }
    Set<String> nestedNames = new HashSet<String>(myUnprocessedNestedMembers);
    myUnprocessedNestedMembers.clear();
    transformNestedMembers(nestedNames);
  }

  private static void transformNestedMembers(Set<String> nestedNames) {
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

  private static List<Class<?>> getSuperClassesAndInterfaces(Class<?> cls) {
    List<Class<?>> result = new ArrayList<Class<?>>();

    Set<String> alreadyProcessed = new HashSet<String>();
    Queue<Class<?>> supersQueue = new LinkedList<Class<?>>();
    supersQueue.add(cls);

    List<Class<?>> unprocessed = new ArrayList<Class<?>>();
    while (!supersQueue.isEmpty()) {
      Class<?> currentCls = supersQueue.poll();
      alreadyProcessed.add(currentCls.getName());
      result.add(currentCls);

      if (COLLECTION_INTERFACE_NAME.equals(currentCls.getName()) || MAP_INTERFACE_NAME.equals(currentCls.getName())) {
        continue;
      }

      Class<?> superCls = currentCls.getSuperclass();
      if (superCls != null && !OBJECT_CLASS_NAME.equals(superCls.getName()) && !alreadyProcessed.contains(superCls.getName())) {
        unprocessed.add(superCls);
      }

      for (Class<?> inter : currentCls.getInterfaces()) {
        if (JAVA_UTIL_PACKAGE_NAME.equals(inter.getPackage().getName()) &&
            !alreadyProcessed.contains(inter.getName())) {
          unprocessed.add(inter);
        }
      }

      if (supersQueue.isEmpty()) {
        supersQueue.addAll(unprocessed);
        unprocessed.clear();
      }
    }

    return result;
  }

  private static Set<String> getClassesNames(List<Class<?>> classes) {
    Set<String> result = new HashSet<String>();
    for (Class<?> cls : classes) {
      result.add(cls.getName());
    }
    return result;
  }

  private static KnownMethodsSet getAllKnownMethods(Class<?> cls, List<Class<?>> supers) {
    String internalClsName = getInternalClsName(cls);
    boolean fairCheckIsNecessary = !internalClsName.startsWith("java/util") ||
                                   internalClsName.split("/").length != 3;

    if (fairCheckIsNecessary) {
      return new KnownMethodsSet();
    }

    int index = supers.indexOf(cls);
    if (index == -1) {
      return new KnownMethodsSet();
    }

    KnownMethodsSet result = new KnownMethodsSet();

    List<Class<?>> clsAndItsSupers = supers.subList(index, supers.size());

    for (Class<?> superCls : clsAndItsSupers) {
      KnownMethodsSet knownMethods = myKnownMethods.get(getInternalClsName(superCls));
      if (knownMethods != null) {
        result.addAll(knownMethods);
      }
    }

    return result;
  }

  private static void transformCollectionClassIfNeeded(Class<?> cls) {
    try {
      myTransformLock.lock();
      String qualifiedClassName = getInternalClsName(cls);
      if (myCollectionsToTransform.containsKey(qualifiedClassName)) {
        return;
      }

      List<Class<?>> allSupers = getSuperClassesAndInterfaces(cls);
      Set<String> allSupersNames = getClassesNames(allSupers);

      for (Class<?> loadedCls : ourInstrumentation.getAllLoadedClasses()) {
        if (allSupersNames.contains(loadedCls.getName())) {
          try {
            myCollectionsToTransform.put(getInternalClsName(loadedCls), getAllKnownMethods(loadedCls, allSupers));
            ourInstrumentation.retransformClasses(loadedCls);
          }
          catch (UnmodifiableClassException e) {
            e.printStackTrace();
          }
        }
      }

      transformCollectionNestedMembers();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    finally {
      myTransformLock.unlock();
    }
  }

  @SuppressWarnings("unused")
  public static void putFieldToCapture(String clsTypeDesc, String fieldName) {
    String internalClsName = getInternalClsName(clsTypeDesc);
    myFieldsToCapture.putIfAbsent(internalClsName, Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>()));
    Set<String> fields = myFieldsToCapture.get(internalClsName);
    fields.add(fieldName);
    myClassesToTransform.add(internalClsName);
  }

  @SuppressWarnings("unused")
  public static void emulateFieldWatchpoint(String... clsNames) {
    for (String clsName : clsNames) {
      transformClassToCaptureFields(clsName);
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

  @SuppressWarnings("unused")
  public static Pair createPair(Object key, Object value) {
    return new Pair(key, value);
  }

  private static class CollectionBreakpointTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
      if (className == null) {
        return null;
      }

      if (myCollectionsToTransform.containsKey(className) || myClassesToTransform.contains(className)) {
        try {
          ClassReader reader = new ClassReader(classfileBuffer);
          ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);

          MyClassVisitor instrumentor = new MyClassVisitor(className, Opcodes.API_VERSION, writer);
          reader.accept(instrumentor, ClassReader.EXPAND_FRAMES);
          byte[] bytes = writer.toByteArray();

          if (DEBUG) {
            writeDebugInfo(className, bytes);
          }

          return bytes;
        }
        catch (Exception e) {
          processFailedToInstrumentError(className, e);
        }
      }
      return null;
    }
  }

  static class MyClassVisitor extends ClassVisitor {
    private final String myClsName;

    private MyClassVisitor(String clsName, int api, ClassVisitor cv) {
      super(api, cv);
      myClsName = clsName;
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
      if (myClassesToTransform.contains(myClsName) && !myClassesToTransform.contains(name)) {
        myUnprocessedNestedMembers.add(name); // save for processing after transform
      }

      boolean isNonStatic = (access & Opcodes.ACC_STATIC) == 0;
      boolean shouldProcess = myCollectionsToTransform.containsKey(myClsName) && !myCollectionsToTransform.containsKey(name);

      if (isNonStatic && shouldProcess) {
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
      MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

      boolean isBridgeMethod = (access & Opcodes.ACC_BRIDGE) != 0;

      if (!isBridgeMethod && name != null && desc != null) {
        if (myClassesToTransform.contains(myClsName)) {
          mv = new CaptureFieldsMethodVisitor(api, mv);
        }

        boolean isNonStaticMethod = (access & Opcodes.ACC_STATIC) == 0;
        boolean isNonSynthetic = (access & Opcodes.ACC_SYNTHETIC) == 0;
        boolean isConstructor = name.equals(CONSTRUCTOR_METHOD_NAME);

        if (isNonStaticMethod && isNonSynthetic && !isConstructor &&
            myCollectionsToTransform.containsKey(myClsName)) {
          CollectionMethodVisitor collectionMethodVisitor = new CollectionMethodVisitor(api, access, name, desc, mv);
          mv = new TryCatchAdapter(api, access, name, desc, signature, exceptions, collectionMethodVisitor);
        }
      }

      return mv;
    }

    private boolean shouldCaptureModifications(String methodFullDesc) {
      KnownMethodsSet knownMethods = myCollectionsToTransform.get(myClsName);
      if (knownMethods == null) {
        return false;
      }
      KnownMethod method = knownMethods.get(methodFullDesc);
      return method == null || method.isMutable();
    }

    private boolean shouldOptimizeCapture(String methodFullDesc) {
      KnownMethodsSet knownMethods = myCollectionsToTransform.get(myClsName);
      if (knownMethods == null) {
        return false;
      }
      return knownMethods.get(methodFullDesc) != null;
    }

    private boolean shouldSynchronize(String methodFullDesc) {
      return !shouldOptimizeCapture(methodFullDesc);
    }

    private static boolean isReturnInstruction(int opcode) {
      return opcode == Opcodes.RETURN || opcode == Opcodes.ARETURN ||
             opcode == Opcodes.IRETURN || opcode == Opcodes.LRETURN ||
             opcode == Opcodes.DRETURN || opcode == Opcodes.FRETURN;
    }

    private class TryCatchAdapter extends MethodNode {
      private final String myMethodFullDesc;

      private TryCatchAdapter(int api, int access, String name, String desc, String signature, String[] exceptions, MethodVisitor mv) {
        super(api, access, name, desc, signature, exceptions);
        this.mv = mv;
        myMethodFullDesc = name + desc;
      }

      @Override
      public void visitEnd() {
        super.visitEnd();
        if (shouldCaptureModifications(myMethodFullDesc)) {
          addTryCatchCode();
        }
        accept(mv);
      }

      private void addTryCatchCode() {
        LabelNode startTryCatch = new LabelNode();
        LabelNode endTryCatch = new LabelNode();
        LabelNode handlerTryCatch = new LabelNode();

        AbstractInsnNode firstIns = instructions.getFirst();
        if (firstIns != null) {
          instructions.insertBefore(firstIns, startTryCatch);
        }
        else {
          instructions.add(startTryCatch);
        }

        InsnList additionalInstructions = new InsnList();
        additionalInstructions.add(endTryCatch);
        additionalInstructions.add(handlerTryCatch);
        addEndCaptureInstructions(additionalInstructions);
        additionalInstructions.add(new InsnNode(Opcodes.ATHROW));

        instructions.add(additionalInstructions);

        TryCatchBlockNode tryCatchBlockNode = new TryCatchBlockNode(startTryCatch, endTryCatch, handlerTryCatch, null);
        tryCatchBlocks.add(tryCatchBlockNode);
      }

      private void addEndCaptureInstructions(InsnList insnList) {
        insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insnList.add(new LdcInsnNode(shouldSynchronize(myMethodFullDesc)));
        insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                                        getInstrumentorClassName(),
                                        ON_CAPTURE_END_METHOD_NAME,
                                        ON_CAPTURE_END_METHOD_DESC,
                                        false));
        maxStack += 1;
      }
    }

    private class CollectionMethodVisitor extends LocalVariablesSorter {
      private final String myMethodFullDesc;
      private int myCollectionCopyVar;
      private int myShouldCaptureVar;
      private int myAdditionalStackSpace = 0;
      private int myNumberOfAdditionalLocalVars = 0;

      protected CollectionMethodVisitor(int api, int access, String name, String descriptor, MethodVisitor methodVisitor) {
        super(api, access, descriptor, methodVisitor);
        myMethodFullDesc = name + descriptor;
      }

      @Override
      public void visitCode() {
        super.visitCode();
        if (shouldCaptureModifications(myMethodFullDesc)) {
          addStartCaptureCode();

          if (!shouldOptimizeCapture(myMethodFullDesc)) {
            addCaptureCollectionCopyCode();
          }
        }
      }

      @Override
      public void visitInsn(int opcode) {
        if (shouldCaptureModifications(myMethodFullDesc) && isReturnInstruction(opcode)) {
          addCaptureCollectionModificationCode();
          addEndCaptureCode();
        }
        super.visitInsn(opcode);
      }

      @Override
      public void visitMaxs(int maxStack, int maxLocals) {
        super.visitMaxs(maxStack + myAdditionalStackSpace, maxLocals + myNumberOfAdditionalLocalVars);
      }

      private void addEndCaptureCode() {
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitLdcInsn(shouldSynchronize(myMethodFullDesc));
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                           getInstrumentorClassName(),
                           ON_CAPTURE_END_METHOD_NAME,
                           ON_CAPTURE_END_METHOD_DESC,
                           false);
        myAdditionalStackSpace += 1;
      }

      private void addStartCaptureCode() {
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitLdcInsn(shouldSynchronize(myMethodFullDesc));
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                           getInstrumentorClassName(),
                           ON_CAPTURE_START_METHOD_NAME,
                           ON_CAPTURE_START_METHOD_DESC,
                           false);
        myShouldCaptureVar = newLocal(Type.BOOLEAN_TYPE);
        mv.visitVarInsn(Opcodes.ISTORE, myShouldCaptureVar);
        myAdditionalStackSpace += 2;
        myNumberOfAdditionalLocalVars += 1;
      }

      private void addCaptureCollectionCopyCode() {
        mv.visitVarInsn(Opcodes.ILOAD, myShouldCaptureVar);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                           getInstrumentorClassName(),
                           CAPTURE_COLLECTION_COPY_METHOD_NAME,
                           CAPTURE_COLLECTION_COPY_METHOD_DESC,
                           false);
        myCollectionCopyVar = newLocal(Type.getType(MULTISET_TYPE));
        mv.visitVarInsn(Opcodes.ASTORE, myCollectionCopyVar);
        myAdditionalStackSpace += 3;
        myNumberOfAdditionalLocalVars += 1;
      }

      private void addCaptureCollectionModificationDefaultCode() {
        mv.visitVarInsn(Opcodes.ALOAD, myCollectionCopyVar);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                           getInstrumentorClassName(),
                           CAPTURE_COLLECTION_MODIFICATION_DEFAULT_METHOD_NAME,
                           CAPTURE_COLLECTION_MODIFICATION_DEFAULT_METHOD_DESC,
                           false);
        myAdditionalStackSpace += 2;
      }

      private void addCaptureCollectionModificationCode() {
        KnownMethodsSet knownMethods = myCollectionsToTransform.get(myClsName);
        KnownMethod knownMethod = knownMethods.get(myMethodFullDesc);
        if (knownMethod == null) {
          addCaptureCollectionModificationDefaultCode();
        }
        else {
          myAdditionalStackSpace += knownMethod.addCaptureModificationCode(mv, myShouldCaptureVar);
        }
      }
    }

    private class CaptureFieldsMethodVisitor extends MethodVisitor {
      private int myAdditionalStackSpace = 0;

      private CaptureFieldsMethodVisitor(int api, MethodVisitor methodVisitor) {
        super(api, methodVisitor);
      }

      @Override
      public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        boolean isPutOperation = opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC;
        if (isPutOperation) {
          visitPutField(mv, opcode, myClsName, owner, name);
        }
        super.visitFieldInsn(opcode, owner, name, descriptor);
      }

      private void visitPutField(MethodVisitor mv, int opcode, String clsName, String owner, String fieldName) {
        Set<String> fieldNames = myFieldsToCapture.get(owner);
        if (fieldNames != null && fieldNames.contains(fieldName)) {
          boolean isStaticField = opcode == Opcodes.PUTSTATIC;
          addCaptureFieldModificationCode(mv, clsName, owner, fieldName, isStaticField);
        }
      }

      @Override
      public void visitMaxs(int maxStack, int maxLocals) {
        super.visitMaxs(maxStack + myAdditionalStackSpace, maxLocals);
      }

      private void addCaptureFieldModificationCode(MethodVisitor mv,
                                                   String clsName,
                                                   String fieldOwner,
                                                   String fieldName,
                                                   boolean isStaticField) {
        putThisObjOnStack(mv, clsName, fieldOwner, isStaticField);
        mv.visitLdcInsn(fieldOwner);
        mv.visitLdcInsn(fieldName);
        mv.visitLdcInsn(true);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                           getInstrumentorClassName(),
                           CAPTURE_FIELD_MODIFICATION_METHOD_NAME,
                           CAPTURE_FIELD_MODIFICATION_METHOD_DESC,
                           false);
        myAdditionalStackSpace += 3;
      }

      private void putThisObjOnStack(MethodVisitor mv, String clsName, String fieldOwner, boolean isStaticField) {
        if (isStaticField) {
          mv.visitInsn(Opcodes.DUP);
          mv.visitInsn(Opcodes.ACONST_NULL);
        }
        else if (clsName.equals(fieldOwner)) {
          mv.visitInsn(Opcodes.DUP);
          mv.visitVarInsn(Opcodes.ALOAD, 0);
        }
        else {
          mv.visitInsn(Opcodes.DUP2);
          mv.visitInsn(Opcodes.SWAP);
        }
        myAdditionalStackSpace += 2;
      }
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

    private Set<Map.Entry<Object, Integer>> entrySet() {
      return myContainer.entrySet();
    }

    public static Multiset toMultiset(Object collection) {
      Multiset multiset = new Multiset();
      if (collection instanceof Collection) {
        for (Object element : (Collection<?>)collection) {
          multiset.add(new Wrapper(element));
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
    private final Map<Object, CollectionInstanceLock> myContainer = new IdentityHashMap<Object, CollectionInstanceLock>();
    private final ReentrantLock myLock = new ReentrantLock();

    public void add(Object obj) {
      myLock.lock();
      try {
        if (!myContainer.containsKey(obj)) {
          myContainer.put(obj, new CollectionInstanceLock());
        }
      }
      finally {
        myLock.unlock();
      }
    }

    public CollectionInstanceLock get(Object obj) {
      myLock.lock();
      try {
        return myContainer.get(obj);
      }
      finally {
        myLock.unlock();
      }
    }
  }

  public static class CollectionInstanceLock {
    private final ReentrantLock myLock = new ReentrantLock();
    private final ThreadLocal<Integer> myMethodEnterNumber = new ThreadLocal<Integer>();

    private CollectionInstanceLock() {
      myMethodEnterNumber.set(0);
    }

    public boolean lock(boolean shouldSynchronized) {
      if (shouldSynchronized) {
        try {
          myLock.tryLock(10, TimeUnit.MINUTES);
        }
        catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      int result = myMethodEnterNumber.get();
      myMethodEnterNumber.set(result + 1);
      return result == 0;
    }

    public void unlock(boolean shouldSynchronized) {
      try {
        myMethodEnterNumber.set(myMethodEnterNumber.get() - 1);
        if (shouldSynchronized) {
          myLock.unlock();
        }
      }
      catch (Throwable e) {
        e.printStackTrace();
      }
    }
  }

  private static class Wrapper {
    private final Object value;

    private Wrapper(Object value) {
      this.value = value;
    }

    private Object getValue() {
      return value;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Wrapper &&
             value == ((Wrapper)obj).value;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(value);
    }
  }

  public static class Pair implements Map.Entry<Object, Object> {
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

  private static class KnownMethodsSet {
    private final Map<String, KnownMethod> myContainer = new HashMap<String, KnownMethod>();

    public void add(KnownMethod method) {
      if (!myContainer.containsKey(method.myMethodFullDesc)) {
        myContainer.put(method.myMethodFullDesc, method);
      }
    }

    public void addAll(KnownMethodsSet set) {
      for (KnownMethod method : set.myContainer.values()) {
        add(method);
      }
    }

    public KnownMethod get(String methodFullDesc) {
      return myContainer.get(methodFullDesc);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof KnownMethodsSet && myContainer.equals(((KnownMethodsSet)obj).myContainer);
    }

    @Override
    public int hashCode() {
      return myContainer.hashCode();
    }
  }

  private abstract static class KnownMethod {
    private final String myMethodFullDesc;
    private final boolean myIsMutable;

    private KnownMethod(String desc, boolean mutable) {
      myMethodFullDesc = desc;
      myIsMutable = mutable;
    }

    private boolean isMutable() {
      return myIsMutable;
    }

    abstract public int addCaptureModificationCode(MethodVisitor mv, int shouldCaptureVar);

    @Override
    public boolean equals(Object obj) {
      return obj instanceof KnownMethod && myMethodFullDesc.equals(((KnownMethod)obj).myMethodFullDesc);
    }

    @Override
    public int hashCode() {
      return myMethodFullDesc.hashCode();
    }
  }

  private static class ImmutableMethod extends KnownMethod {
    private ImmutableMethod(String desc) {
      super(desc, false);
    }

    @Override
    public int addCaptureModificationCode(MethodVisitor mv, int shouldCaptureVar) {
      return 0;
    }
  }

  private static class ReturnsBooleanMethod extends KnownMethod {
    private final boolean myIsAddition;

    private ReturnsBooleanMethod(String desc, boolean isAddition) {
      super(desc, true);
      myIsAddition = isAddition;
    }

    @Override
    public int addCaptureModificationCode(MethodVisitor mv, int shouldCaptureVar) {
      mv.visitInsn(Opcodes.DUP);
      mv.visitVarInsn(Opcodes.ILOAD, shouldCaptureVar);
      mv.visitInsn(Opcodes.SWAP);
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitVarInsn(Opcodes.ALOAD, 1);
      mv.visitLdcInsn(myIsAddition);
      mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                         getInstrumentorClassName(),
                         CAPTURE_COLLECTION_MODIFICATION_METHOD_NAME,
                         CAPTURE_COLLECTION_MODIFICATION_METHOD_DESC,
                         false);
      return 5;
    }
  }

  private static class PutMethod extends KnownMethod {
    private PutMethod() {
      super("put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
    }

    @Override
    public int addCaptureModificationCode(MethodVisitor mv, int shouldCaptureVar) {
      mv.visitInsn(Opcodes.DUP);
      mv.visitVarInsn(Opcodes.ALOAD, 2);
      Label label = new Label();
      Label end = new Label();
      mv.visitJumpInsn(Opcodes.IF_ACMPNE, label);
      mv.visitLdcInsn(false);
      mv.visitJumpInsn(Opcodes.GOTO, end);
      mv.visitLabel(label);
      mv.visitLdcInsn(true);
      mv.visitLabel(end);
      mv.visitVarInsn(Opcodes.ILOAD, shouldCaptureVar);
      mv.visitInsn(Opcodes.SWAP);
      mv.visitVarInsn(Opcodes.ALOAD, 1);
      mv.visitVarInsn(Opcodes.ALOAD, 2);
      mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                         getInstrumentorClassName(),
                         CREATE_PAIR_METHOD_NAME,
                         CREATE_PAIR_METHOD_DESC,
                         false);
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitInsn(Opcodes.SWAP);
      mv.visitLdcInsn(true);
      mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                         getInstrumentorClassName(),
                         CAPTURE_COLLECTION_MODIFICATION_METHOD_NAME,
                         CAPTURE_COLLECTION_MODIFICATION_METHOD_DESC,
                         false);
      return 6;
    }
  }

  private static class RemoveKeyMethod extends KnownMethod {
    private RemoveKeyMethod() {
      super("remove(Ljava/lang/Object;)Ljava/lang/Object;", true);
    }

    @Override
    public int addCaptureModificationCode(MethodVisitor mv, int shouldCaptureVar) {
      mv.visitInsn(Opcodes.DUP);
      mv.visitInsn(Opcodes.DUP);
      Label label = new Label();
      Label end = new Label();
      mv.visitJumpInsn(Opcodes.IFNONNULL, label);
      mv.visitLdcInsn(false);
      mv.visitJumpInsn(Opcodes.GOTO, end);
      mv.visitLabel(label);
      mv.visitLdcInsn(true);
      mv.visitLabel(end);
      mv.visitVarInsn(Opcodes.ILOAD, shouldCaptureVar);
      mv.visitInsn(Opcodes.DUP_X2);
      mv.visitInsn(Opcodes.POP);
      mv.visitInsn(Opcodes.DUP_X1);
      mv.visitInsn(Opcodes.POP);
      mv.visitVarInsn(Opcodes.ALOAD, 1);
      mv.visitInsn(Opcodes.SWAP);
      mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                         getInstrumentorClassName(),
                         CREATE_PAIR_METHOD_NAME,
                         CREATE_PAIR_METHOD_DESC,
                         false);
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitInsn(Opcodes.SWAP);
      mv.visitLdcInsn(false);
      mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                         getInstrumentorClassName(),
                         CAPTURE_COLLECTION_MODIFICATION_METHOD_NAME,
                         CAPTURE_COLLECTION_MODIFICATION_METHOD_DESC,
                         false);
      return 7;
    }
  }
}