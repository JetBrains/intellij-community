// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.FailSafeClassReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.AnnotationVisitor;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.FieldVisitor;
import org.jetbrains.org.objectweb.asm.Label;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.RecordComponentVisitor;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.TypePath;
import org.jetbrains.org.objectweb.asm.signature.SignatureReader;
import org.jetbrains.org.objectweb.asm.signature.SignatureVisitor;
import org.jetbrains.org.objectweb.asm.tree.AnnotationNode;
import org.jetbrains.org.objectweb.asm.tree.FieldNode;
import org.jetbrains.org.objectweb.asm.tree.InsnNode;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class JavaAbiClassFilter extends ClassVisitor {
  public enum Mode {
    IJAR_COMPLIANT,     // kept methods carry no Code attribute, like the output of the 'ijar' tool from Bazel's rules_java; an attempt to load such bytecode may produce ClassFormatError
    VERIFIABLE_BYTECODE // the same class filtering policy, but the bytecode stays loadable (JVMS §4.10): kept methods get minimal bodies, a class keeps its static initializer, and an enum keeps its default methods functional
  }

  private final Mode myMode;
  private boolean isEnum;
  private boolean allowPackageLocalMethods;
  private String myName;
  private boolean myAbiVisible = true;
  private final List<FieldNode> myFields = new ArrayList<>();
  private final MethodContainer myMethods;
  private final InnerClassInfoContainer myInnerClasses = new InnerClassInfoContainer();
  private final Set<String> myReferencedClasses = new HashSet<>();
  private final List<String> myNestMembers = new ArrayList<>();
  private final List<String> mySignatures = new ArrayList<>();

  private JavaAbiClassFilter(ClassVisitor delegate, MethodContainer methodContainer, Mode mode) {
    super(Opcodes.API_VERSION, delegate);
    myMethods = methodContainer;
    myMode = mode;
  }

  public static byte @Nullable [] filter(byte[] classBytes) {
    return filter(Mode.VERIFIABLE_BYTECODE, classBytes);
  }

  public static byte @Nullable [] filter(Mode mode, byte[] classBytes) {
    ClassReader reader = new FailSafeClassReader(classBytes);
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS) {
      @Override
      protected String getCommonSuperClass(String type1, String type2) {
        return null;
      }
    };

    JavaAbiClassFilter visitor = new JavaAbiClassFilter(writer, MethodContainer.create(reader, mode), mode);
    // Stripping certain DEBUG-INFO from abi.jar might lead to bytecode differences between compilation results against some artifact and abi-version of this artifact.
    // This won't affect the behavior of the resulting bytecode. However, if such differences are not desired, parameter DEBUG-INFO should be kept.
    reader.accept(
      visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES /*| ClassReader.SKIP_DEBUG*/
    );

    // Both modes remove only the classes that no source can name: local and anonymous classes,
    // and every class nested inside them at any depth. A NAMED class stays regardless of its
    // declared visibility: a kept file can still reference it through a supertype constant, a
    // PermittedSubclasses list, a member descriptor, a signature, or an annotation value, and
    // such a reference is not visible from the referenced class's own file (e.g. DateFormatUtil$CF).
    if (!visitor.myAbiVisible) {
      return null;
    }

    return writer.toByteArray();
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    myName = name;
    isEnum = isEnum(access);
    allowPackageLocalMethods = name.contains("/android/");   // todo: temporary condition to enable android tests compilation

    myReferencedClasses.add(name);
    if (superName != null) {
      myReferencedClasses.add(superName);
    }
    Collections.addAll(myReferencedClasses, interfaces);
    if (signature != null) {
      mySignatures.add(signature);
    }

    super.visit(version, access, name, signature, superName, interfaces);
  }

  private static boolean isAbiVisible(int access) {
    // include to ABI surface public, protected and package-local members
    // package-local members are included only to match the behavior of the 'ijar' tool from standard rules_java
    return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0 || isPackageLocal(access);
  }

  private static boolean isEnum(int access) {
    return (access & Opcodes.ACC_ENUM) != 0;
  }

  private static boolean isSynthetic(int access) {
    return (access & Opcodes.ACC_SYNTHETIC) != 0;
  }

  private static boolean isStatic(int access) {
    return (access & Opcodes.ACC_STATIC) != 0;
  }

  private static boolean isPackageLocal(int access) {
    return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE)) == 0;
  }

  @Override
  public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
    // in VERIFIABLE_BYTECODE mode an enum also keeps its synthetic fields ($VALUES): the kept
    // real bodies of the default enum methods read them
    boolean keep = myMode == Mode.IJAR_COMPLIANT? isAbiVisible(access) : isAbiVisible(access) || isEnum && isSynthetic(access);
    if (keep) {
      FieldNode field = new FieldNode(Opcodes.API_VERSION, access, name, descriptor, signature, value);
      myFields.add(field);
      collectReferencedTypes(Type.getType(descriptor));
      if (signature != null) {
        mySignatures.add(signature);
      }
      return field;
    }
    return null;
  }

  private void collectReferencedTypes(Type type) {
    if (type.getSort() == Type.OBJECT) {
      myReferencedClasses.add(type.getInternalName());
    }
    if (type.getSort() == Type.ARRAY) {
      collectReferencedTypes(type.getElementType());
    }
    else if (type.getSort() == Type.METHOD) {
      collectReferencedTypes(type.getReturnType());
      for (Type argType : type.getArgumentTypes()) {
        collectReferencedTypes(argType);
      }
    }
  }

  /**
   * Resolves the collected generic signatures into class references. A signature encodes an
   * inner class as a SIMPLE name that continues its enclosing class type. 
   */
  private void collectReferencedTypesFromSignatures() {
    if (mySignatures.isEmpty()) {
      return;
    }
    SignatureVisitor resolver = new SignatureVisitor(Opcodes.API_VERSION) {
      private static final String UNRESOLVED = "";
      private final Deque<String> myTypeStack = new ArrayDeque<>();

      @Override
      public void visitClassType(String name) {
        // the signature names the outermost class type with its full internal name
        myTypeStack.push(name);
        myReferencedClasses.add(name);
      }

      @Override
      public void visitInnerClassType(String name) {
        String enclosing = myTypeStack.poll();
        String resolved = enclosing == null || enclosing.equals(UNRESOLVED)
          ? UNRESOLVED
          : Objects.requireNonNullElse(myInnerClasses.find(enclosing, name), UNRESOLVED);
        myTypeStack.push(resolved);
        if (!resolved.equals(UNRESOLVED)) {
          myReferencedClasses.add(resolved);
        }
      }

      @Override
      public void visitEnd() {
        myTypeStack.poll();
      }
    };
    for (String signature : mySignatures) {
      new SignatureReader(signature).accept(resolver);
    }
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
    if (myMode == Mode.IJAR_COMPLIANT && "<clinit>".equals(name)) {
      // ijar always deletes the static initializer: it is never part of the compile-time API.
      // The VERIFIABLE_BYTECODE mode keeps it: as a stub for a regular class, and with the real
      // body for an enum (see EnumMethodContainer).
      return null;
    }
    if (myMode == Mode.IJAR_COMPLIANT && isSynthetic(access) && (access & Opcodes.ACC_BRIDGE) == 0) {
      // ijar drops synthetic non-bridge methods (the access$NNN accessors); bridges stay,
      // which is why this repo forces ijar over turbine
      return null;
    }
    if (isAbiVisible(access) || (allowPackageLocalMethods && isPackageLocal(access))) {
      MethodNode visitor = myMethods.addAbiStubMethod(access, name, descriptor, signature, exceptions);
      if (visitor != null) {
        collectReferencedTypes(Type.getMethodType(descriptor));
        if (signature != null) {
          mySignatures.add(signature);
        }
        if (exceptions != null) {
          Collections.addAll(myReferencedClasses, exceptions);
        }
      }
      return visitor;
    }
    return null;
  }

  @Override
  public void visitEnd() {
    // Important: sorting members may cause generated bytecode built against ABI content
    // to be binary-different from the generated bytecode built against artifact's bytecode
    // For now, sorting is disabled to minimize bytecode differences

    // process postponed entries in the InnerClasses attribute
    // the reference set must be consistent and complete for BOTH modes: signature-only
    // references keep InnerClasses entries in the VERIFIABLE_BYTECODE mode too
    collectReferencedTypesFromSignatures();
    // class-literal values of the kept members' annotations reference classes too; the
    // class-level annotations are covered by AnnotationValueRefCollector during the visit
    for (FieldNode field : myFields) {
      collectAnnotationValueRefs(field.visibleAnnotations, field.invisibleAnnotations, field.visibleTypeAnnotations, field.invisibleTypeAnnotations);
    }
    for (MethodNode method : myMethods) {
      collectAnnotationValueRefs(method.visibleAnnotations, method.invisibleAnnotations, method.visibleTypeAnnotations, method.invisibleTypeAnnotations);
      collectParameterAnnotationValueRefs(method.visibleParameterAnnotations);
      collectParameterAnnotationValueRefs(method.invisibleParameterAnnotations);
      collectAnnotationValue(method.annotationDefault);
    }

    emitInnerClassesAndNestMembers();

    //Collections.sort(myFields, Comparator.comparing(f -> f.name));
    for (FieldNode field : myFields) {
      field.accept(cv);
    }

    //Collections.sort(myMethods, Comparator.comparing(m -> m.name));
    for (MethodNode method : myMethods) {
      method.accept(cv);
    }
    super.visitEnd();
  }

  /**
   * Emits the kept InnerClasses entries and the kept NestMembers names. The logic is shared by both modes.
   */
  private void emitInnerClassesAndNestMembers() {
    InnerClassInfo self = myInnerClasses.find(myName);
    if (self != null) {
      myAbiVisible = myInnerClasses.isNameable(self);
    }

    // A NestMembers name is a class constant, so a kept member must keep its InnerClasses entry
    // (JVMS §4.7.6). The filter drops the class files of local and anonymous members and of
    // everything nested inside them, so their NestMembers names are pruned together with the
    // files. The member's own InnerClasses entry in this class file is the evidence; a member
    // without an entry is kept.
    List<String> keptNestMembers = new ArrayList<>();
    for (String member : myNestMembers) {
      InnerClassInfo cls = myInnerClasses.find(member);
      if (cls == null || myInnerClasses.isNameable(cls)) {
        keptNestMembers.add(member);
        myReferencedClasses.add(member);
      }
    }

    Set<String> kept = new HashSet<>();
    for (InnerClassInfo cls : myInnerClasses) {
      if (shouldKeep(cls)) {
        // a kept entry keeps its whole enclosing chain, so that the attribute stays
        // self-consistent: the intermediate entries of referenced deep classes stay too
        for (InnerClassInfo c = cls; c != null; c = myInnerClasses.getEnclosing(c)) {
          if (!kept.add(c.name)) {
            break; // the rest of the chain is already kept
          }
        }
      }
    }

    for (InnerClassInfo cls : myInnerClasses) {
      if (kept.contains(cls.name)) {
        cv.visitInnerClass(cls.name, cls.outerName, cls.innerName, cls.access);
      }
    }
    for (String member : keptNestMembers) {
      cv.visitNestMember(member);
    }
  }

  private boolean shouldKeep(@NotNull InnerClassInfo cls) {
    if (cls.isLocal() || cls.isAnonymous()) {
      return false;
    }
    // a named direct child of this class stays even when unreferenced
    return myReferencedClasses.contains(cls.name) || Objects.equals(cls.outerName, myName) || Objects.equals(cls.name, myName);
  }

  @Override
  public void visitNestHost(String nestHost) {
    myReferencedClasses.add(nestHost);
    super.visitNestHost(nestHost);
  }

  @Override
  public void visitNestMember(String nestMember) {
    // postpone: visitEnd emits the members each mode keeps. A member name must
    // not enter myReferencedClasses here: membership alone keeps nothing, counting it would.
    myNestMembers.add(nestMember);
  }

  @Override
  public void visitPermittedSubclass(String permittedSubclass) {
    myReferencedClasses.add(permittedSubclass);
    super.visitPermittedSubclass(permittedSubclass);
  }

  @Override
  public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
    collectReferencedTypes(Type.getType(descriptor));
    return super.visitRecordComponent(name, descriptor, signature);
  }

  @Override
  public void visitInnerClass(String name, String outerName, String innerName, int access) {
    // postpone decision until it is known which classes are referenced
    myInnerClasses.add(new InnerClassInfo(name, outerName, innerName, access));
  }

  @Override
  public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
    // the annotation TYPE is not a reference in either mode - see AnnotationValueRefCollector
    return new AnnotationValueRefCollector(super.visitAnnotation(descriptor, visible));
  }

  @Override
  public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
    return new AnnotationValueRefCollector(super.visitTypeAnnotation(typeRef, typePath, descriptor, visible));
  }

  @SafeVarargs
  private void collectAnnotationValueRefs(List<? extends AnnotationNode>... annotationLists) {
    for (List<? extends AnnotationNode> annotations : annotationLists) {
      if (annotations != null) {
        for (AnnotationNode annotation : annotations) {
          if (annotation.values != null) {
            for (Object value : annotation.values) {
              collectAnnotationValue(value);
            }
          }
        }
      }
    }
  }

  private void collectParameterAnnotationValueRefs(List<AnnotationNode> @Nullable [] parameterAnnotations) {
    if (parameterAnnotations != null) {
      for (List<AnnotationNode> annotations : parameterAnnotations) {
        collectAnnotationValueRefs(annotations);
      }
    }
  }

  private void collectAnnotationValue(@Nullable Object value) {
    if (value instanceof Type type) {
      collectReferencedTypes(type);
    }
    else if (value instanceof AnnotationNode nested) {
      collectAnnotationValueRefs(List.of(nested));
    }
    else if (value instanceof List<?> array) {
      for (Object element : array) {
        collectAnnotationValue(element);
      }
    }
  }

  /**
   * Collects class-literal annotation VALUES into the reference set. In both modes an annotation
   * TYPE is not a reference: consumers resolve it through the type's own class file, and real
   * ijar drops its InnerClasses entry (the ApiStatus$Internal probe). A class literal in an
   * annotation value does count: real ijar keeps its entry (the ExtraHosts$Deserializer probe,
   * a Jackson @JsonDeserialize(using=...) value).
   */
  private final class AnnotationValueRefCollector extends AnnotationVisitor {
    AnnotationValueRefCollector(@Nullable AnnotationVisitor delegate) {
      super(Opcodes.API_VERSION, delegate);
    }

    @Override
    public void visit(String name, Object value) {
      if (value instanceof Type type) {
        collectReferencedTypes(type);
      }
      super.visit(name, value);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      return new AnnotationValueRefCollector(super.visitAnnotation(name, descriptor));
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      return new AnnotationValueRefCollector(super.visitArray(name));
    }
  }

  @Override
  public void visitSource(String source, String debug) {
    // skip source information
  }

  private static final class AbiMethod extends MethodNode {
    private static final List<InsnNode> ourBodyInstructions = List.of(
      new InsnNode(Opcodes.ACONST_NULL),
      new InsnNode(Opcodes.ATHROW)
    );
    private final Label myMethodStart = new Label();
    private final Label myMethodEnd = new Label();
    private final int myParamsSize;

    AbiMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
      super(Opcodes.API_VERSION, access, name, descriptor, signature, exceptions);
      myParamsSize = (Type.getArgumentsAndReturnSizes(desc) >> 2) - (isStatic(access)? 1 : 0);
      if ((access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
        // in a valid bytecode, non-abstract and non-native methods must have a code attribute
        instructions.add(getLabelNode(myMethodStart));
        for (InsnNode insn : ourBodyInstructions) {
          instructions.add(insn);
        }
        instructions.add(getLabelNode(myMethodEnd));
      }
    }

    @Override
    public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
      if (index < myParamsSize) {
        // keep local variable DEBUG info for method parameters only
        super.visitLocalVariable(name, descriptor, signature, myMethodStart, myMethodEnd, index);
      }
      // skip all other local vars
    }

    @Override
    public void visitLineNumber(int line, Label start) {
      // skip line numbers
    }
  }

  /**
   * The ijar-way method generation: no Code attribute at all, even for non-abstract methods.
   * The class file is not valid for the VM, but javac and kotlinc accept it for resolution purposes
   */
  private static final class IjarMethod extends MethodNode {
    IjarMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
      super(Opcodes.API_VERSION, access, name, descriptor, signature, exceptions);
    }

    @Override
    public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
      // no Code attribute means no local variable table
    }

    @Override
    public void visitLineNumber(int line, Label start) {
      // no Code attribute means no line numbers
    }
  }

  private interface MethodContainer extends Iterable<MethodNode> {
    @Nullable
    MethodNode addAbiStubMethod(int access, String name, String descriptor, String signature, String[] exceptions);

    static MethodContainer create(ClassReader reader, Mode mode) {
      if (mode == Mode.VERIFIABLE_BYTECODE && isEnum(reader.getAccess())) {
        // A loaded enum class must stay functional: keep the default enum methods and the static
        // initializer with their real bodies, so the enum constants initialize and values() and
        // valueOf() work when a tool loads the ABI class.
        return new EnumMethodContainer(Opcodes.API_VERSION, reader);
      }
      return new MethodContainer() {
        private final List<MethodNode> myNodes = new ArrayList<>();
        @Override
        public MethodNode addAbiStubMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
          MethodNode node = mode == Mode.IJAR_COMPLIANT? new IjarMethod(access, name, descriptor, signature, exceptions) : new AbiMethod(access, name, descriptor, signature, exceptions);
          myNodes.add(node);
          return node;
        }

        @Override
        public @NotNull Iterator<MethodNode> iterator() {
          return myNodes.iterator();
        }
      };
    }
  }

  private static class EnumMethodContainer implements MethodContainer {
    private static final Set<String> ourEnumMethodsToKeep = Set.of(
      "valueOf", "values", "$values", "name", "ordinal", "compareTo"
    );
    private final Map<String, MethodNode> myNodes = new LinkedHashMap<>(); // keep method order

    EnumMethodContainer(int api, ClassReader reader) {
      if (isAbiVisible(reader.getAccess())) {
        // collect methods to keep
        reader.accept(new ClassVisitor(api) {
          @Override
          public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (shouldKeepMethod(access, name)) {
              MethodNode node = new MethodNode(api, access, name, descriptor, signature, exceptions);
              myNodes.put(getKey(name, descriptor), node);
              return node;
            }

            myNodes.put(getKey(name, descriptor), new AbiMethod(access, name, descriptor, signature, exceptions));
            return null;
          }
        }, ClassReader.SKIP_DEBUG);
      }
    }

    @Override
    @Nullable
    public MethodNode addAbiStubMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
      return shouldKeepMethod(access, name)? null : myNodes.computeIfAbsent(getKey(name, descriptor), _ -> new AbiMethod(access, name, descriptor, signature, exceptions));
    }

    @Override
    public @NotNull Iterator<MethodNode> iterator() {
      return myNodes.values().iterator();
    }

    private static @NotNull String getKey(String name, String descriptor) {
      return name + descriptor;
    }

    private static boolean shouldKeepMethod(int access, String name) {
      return isSynthetic(access) || isAbiVisible(access) && ourEnumMethodsToKeep.contains(name) || isConstructor(name);
    }

    private static boolean isConstructor(String name) {
      return "<init>".equals(name) || "<clinit>".equals(name);
    }
  }

  private record InnerClassInfo(String name, String outerName, String innerName, int access) {
    boolean isAnonymous() {
      return innerName == null; // JVMS 4.7.6: an anonymous class has no simple name
    }

    boolean isLocal() {
      return outerName == null && innerName != null; // JVMS 4.7.6: a local class is not a member of any class
    }
  }

  private static final class InnerClassInfoContainer implements Iterable<InnerClassInfo> {
    private final List<InnerClassInfo> myEntries = new ArrayList<>();
    private final Map<String, InnerClassInfo> myByName = new HashMap<>();
    private final Map<String, Map<String, String>> myNameByEnclosingAndSimple = new HashMap<>();

    void add(@NotNull InnerClassInfo cls) {
      myEntries.add(cls);
      myByName.put(cls.name, cls);
      if (cls.outerName != null && cls.innerName != null) {
        myNameByEnclosingAndSimple.computeIfAbsent(cls.outerName, _ -> new HashMap<>()).put(cls.innerName, cls.name);
      }
    }

    @Override
    public Iterator<InnerClassInfo> iterator() {
      return myEntries.iterator();
    }

    @Nullable
    InnerClassInfo find(@NotNull String name) {
      return myByName.get(name);
    }

    @Nullable
    String find(@NotNull String enclosingName, @NotNull String simpleName) {
      return myNameByEnclosingAndSimple.getOrDefault(enclosingName, Map.of()).get(simpleName);
    }

    /** The entry of the class that encloses the given class, or null when there is no such entry. */
    @Nullable
    InnerClassInfo getEnclosing(@NotNull InnerClassInfo cls) {
      return cls.outerName != null? myByName.get(cls.outerName) : null;
    }

    /**
     * A class belongs to the ABI only when source code can name it: the class and every link of
     * its enclosing chain must be a named class. A local or an anonymous class is not nameable,
     * cannot be extended, cannot be a permitted subclass, and cannot appear in a member's type,
     * so nothing nested under it is reachable either. The chain comes from this class file's own
     * InnerClasses entries.
     */
    boolean isNameable(@NotNull InnerClassInfo cls) {
      for (InnerClassInfo c = cls; c != null; c = getEnclosing(c)) {
        if (c.isLocal() || c.isAnonymous()) {
          return false;
        }
      }
      return true;
    }
  }
}
