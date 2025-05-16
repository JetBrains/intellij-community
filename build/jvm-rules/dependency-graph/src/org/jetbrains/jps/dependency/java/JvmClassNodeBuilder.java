// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import com.dynatrace.hash4j.hashing.HashStream64;
import com.dynatrace.hash4j.hashing.Hashing;
import kotlin.metadata.Attributes;
import kotlin.metadata.KmDeclarationContainer;
import kotlin.metadata.KmFunction;
import kotlin.metadata.KmProperty;
import kotlin.metadata.jvm.JvmExtensionsKt;
import kotlin.metadata.jvm.JvmMethodSignature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.NodeBuilder;
import org.jetbrains.jps.dependency.ReferenceID;
import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.util.Iterators;
import org.jetbrains.jps.util.Ref;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.signature.SignatureReader;
import org.jetbrains.org.objectweb.asm.signature.SignatureVisitor;
import org.jetbrains.org.objectweb.asm.util.Textifier;
import org.jetbrains.org.objectweb.asm.util.TraceMethodVisitor;

import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class JvmClassNodeBuilder extends ClassVisitor implements NodeBuilder {
  private static final Logger LOG = Logger.getLogger("#" + JvmClassNodeBuilder.class.getName());

  private static final Iterable<JvmDifferentiateStrategy> ourDifferentiateStrategies = Iterators.collect(ServiceLoader.load(JvmDifferentiateStrategy.class), new ArrayList<>());

  public static final String LAMBDA_FACTORY_CLASS = "java/lang/invoke/LambdaMetafactory";
  private static final String KOTLIN_LAMBDA_USAGE_CLASS_MARKER = "$sam$";
  private static final int ASM_API_VERSION = Opcodes.API_VERSION;

  private final class AnnotationRetentionPolicyCrawler extends AnnotationVisitor {

    private AnnotationRetentionPolicyCrawler() {
      super(ASM_API_VERSION);
    }

    @Override
    public void visit(String name, Object value) {
    }

    @Override
    public void visitEnum(String name, String desc, String value) {
      myRetentionPolicy = RetentionPolicy.valueOf(value);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String desc) {
      return null;
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      return null;
    }

    @Override
    public void visitEnd() {
    }
  }

  private final class AnnotationTargetCrawler extends AnnotationVisitor {

    private AnnotationTargetCrawler() {
      super(ASM_API_VERSION);
    }

    @Override
    public void visit(String name, Object value) {
    }

    @Override
    public void visitEnum(final String name, String desc, final String value) {
      myTargets.add(ElemType.valueOf(value));
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String desc) {
      return this;
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      return this;
    }

    @Override
    public void visitEnd() {
    }
  }

  private final class AnnotationCrawler extends AnnotationVisitor {

    private final TypeRepr.ClassType myType;
    private final ElemType myTarget;
    private final ContentHashBuilder myHashBuilder;
    private final Consumer<ElementAnnotation> myResultConsumer;

    private final Set<String> myUsedArguments = new HashSet<>();

    private AnnotationCrawler(final TypeRepr.ClassType type, final ElemType target, Consumer<ElementAnnotation> resultConsumer) {
      super(ASM_API_VERSION);
      this.myType = type;
      this.myTarget = target;
      myResultConsumer = resultConsumer;

      // Do not track changes in the annotation's content, if there are no registered annotation trackers that would process these changes.
      // Some technical annotations (e.g. DebugInfo) may contain different content after every compiler run => they will always be considered "changed".
      // Handling such changes may involve additional type-consuming analysis and unnecessary dependency data updates.
      myHashBuilder = isAnnotationTracked(type)? ContentHashBuilder.create() : ContentHashBuilder.NULL;

      final Set<ElemType> targets = myAnnotationTargets.get(type);
      if (targets == null) {
        myAnnotationTargets.put(type, EnumSet.of(target));
      }
      else {
        targets.add(target);
      }
      addUsage(new ClassUsage(type.getJvmName()));
    }

    private String getMethodDescr(final Object value, boolean isArray) {
      final StringBuilder descriptor = new StringBuilder();
      descriptor.append("()");
      if (isArray) {
        descriptor.append("[");
      }
      if (value instanceof Type) {
        descriptor.append("Ljava/lang/Class;");
      }
      else {
        final String name = Type.getType(value.getClass()).getInternalName();
        // only primitive, String, Class, Enum, another Annotation or array of any of these are allowed
        switch (name) {
          case "java/lang/Integer":
            descriptor.append("I");
            break;
          case "java/lang/Short":
            descriptor.append("S");
            break;
          case "java/lang/Long":
            descriptor.append("J");
            break;
          case "java/lang/Byte":
            descriptor.append("B");
            break;
          case "java/lang/Char":
            descriptor.append("C");
            break;
          case "java/lang/Boolean":
            descriptor.append("Z");
            break;
          case "java/lang/Float":
            descriptor.append("F");
            break;
          case "java/lang/Double":
            descriptor.append("D");
            break;
          default:
            descriptor.append("L").append(name).append(";");
            break;
        }
      }
      return descriptor.toString();
    }

    private @Nullable String myArrayName;

    @Override
    public void visit(String name, Object value) {
      final boolean isArray = name == null && myArrayName != null;
      final String argName;
      if (name != null) {
        argName = name;
      }
      else {
        argName = myArrayName;
        // not interested in collecting complete array value; need to know just an array type
        myArrayName = null;
      }
      registerUsages(argName, () -> getMethodDescr(value, isArray), value);
    }

    @Override
    public void visitEnum(String name, String desc, String value) {
      final boolean isArray = name == null && myArrayName != null;
      final String argName;
      if (name != null) {
        argName = name;
      }
      else {
        argName = myArrayName;
        // not interested in collecting complete array value; need to know just an array type
        myArrayName = null;
      }
      registerUsages(argName, () -> (isArray? "()[" : "()") + desc, value);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String desc) {
      return new AnnotationCrawler((TypeRepr.ClassType)TypeRepr.getType(desc), myTarget, res -> myHashBuilder.update(res.getContentHash()));
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      myArrayName = name;
      return this;
    }

    private void registerUsages(@Nullable String methodName, Supplier<String> methodDescr, Object value) {
      if (value instanceof Type) {
        final String className = ((Type)value).getClassName().replace('.', '/');
        addUsage(new ClassUsage(className));
      }
      if (methodName != null) {
        addUsage(new MethodUsage(myType.getJvmName(), methodName, methodDescr.get()));
      myUsedArguments.add(methodName);
        myHashBuilder.update(methodName);
      }
      myHashBuilder.update(value);
    }

    @Override
    public void visitEnd() {
      try {
        Set<String> s = myAnnotationArguments.get(myType);
        if (s == null) {
          myAnnotationArguments.put(myType, myUsedArguments);
        }
        else {
          s.retainAll(myUsedArguments);
        }
      }
      finally {
        myResultConsumer.accept(new ElementAnnotation(myType, myHashBuilder.getResult()));
      }
    }
  }

  private static final class KotlinMetadataCrawler extends AnnotationVisitor {

    private final Consumer<KotlinMeta> myResultConsumer;

    private static final class DataField<T> {
      private final String myName;
      private final Class<T> myType;

      DataField(String name, Class<T> type) {
        myName = name;
        myType = type;
      }

      public T get(Map<String, Object> data) {
        Object val = data.get(myName);
        return myType.isInstance(val)? myType.cast(val) : null;
      }
    }

    private static final DataField<Integer> KIND = new DataField<>("k", Integer.class);
    private static final DataField<int[]> VERSION = new DataField<>("mv", int[].class);
    private static final DataField<String[]> DATA1 = new DataField<>("d1", String[].class);
    private static final DataField<String[]> DATA2 = new DataField<>("d2", String[].class);
    private static final DataField<String> EXTRA_STRING = new DataField<>("xs", String.class);
    private static final DataField<String> PACKAGE_NAME = new DataField<>("pn", String.class);
    private static final DataField<Integer> EXTRA_INT = new DataField<>("xi", Integer.class);

    private final Map<String, Object> myData = new HashMap<>();

    private KotlinMetadataCrawler(Consumer<KotlinMeta> resultConsumer) {
      super(ASM_API_VERSION);
      myResultConsumer = resultConsumer;
    }

    @Override
    public void visit(String name, Object value) {
      myData.put(name, value);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      return new AnnotationVisitor(ASM_API_VERSION) {
        private final List<Object> values = new ArrayList<>();

        @Override
        public void visit(String name, Object value) {
          if (value != null) {
            values.add(value);
          }
        }

        @Override
        public void visitEnd() {
          if (!values.isEmpty()) {
            myData.put(name, values.toArray((Object[])Array.newInstance(values.iterator().next().getClass(), values.size())));
          }
        }
      };
    }

    @Override
    public void visitEnd() {
      Integer kind = KIND.get(myData);
      if (kind != null) {
        Integer extraInt = EXTRA_INT.get(myData);
        myResultConsumer.accept(new KotlinMeta(
          kind, VERSION.get(myData), DATA1.get(myData), DATA2.get(myData), EXTRA_STRING.get(myData), PACKAGE_NAME.get(myData), extraInt == null? 0 : extraInt
        ));
      }
    }
  }

  private final class ModuleCrawler extends ModuleVisitor {

    ModuleCrawler() {
      super(ASM_API_VERSION);
    }

    @Override
    public void visitMainClass(String mainClass) {
      addUsage(new ClassUsage(mainClass));
    }

    @Override
    public void visitRequire(String module, int access, String version) {
      if (isExplicit(access)) {
        // collect non-synthetic dependencies only
        myModuleRequires.add(new ModuleRequires(new JVMFlags(access), module, version));
      }
    }

    @Override
    public void visitExport(String packaze, int access, String... modules) {
      if (isExplicit(access)) {
        // collect non-synthetic dependencies only
        myModuleExports.add(new ModulePackage(packaze, modules != null? Arrays.asList(modules) : Collections.emptyList()));
      }
    }

    @Override
    public void visitUse(String service) {
      addUsage(new ClassUsage(service));
    }

    @Override
    public void visitProvide(String service, String... providers) {
      addUsage(new ClassUsage(service));
      if (providers != null) {
        for (String provider : providers) {
          addUsage(new ClassUsage(provider));
        }
      }
    }

    private boolean isExplicit(int access) {
      return (access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_MANDATED)) == 0;
    }
  }

  private void processSignature(final String sig) {
    if (sig != null) {
      try {
        new SignatureReader(sig).accept(mySignatureCrawler);
      }
      catch (Exception e) {
        LOG.log(Level.INFO, "Problems parsing signature \"" + sig + "\" in " + myFileName, e);
      }
    }
  }

  private final SignatureVisitor mySignatureCrawler = new BaseSignatureVisitor() {
    @Override
    public SignatureVisitor visitClassBound() {
      return mySignatureWithGenericBoundUsageCrawler;
    }

    @Override
    public SignatureVisitor visitInterfaceBound() {
      return mySignatureWithGenericBoundUsageCrawler;
    }

    @Override
    public SignatureVisitor visitTypeArgument(char wildcard) {
      return wildcard == '+' || wildcard == '-'? mySignatureWithGenericBoundUsageCrawler : super.visitTypeArgument(wildcard);
    }
  };

  private final SignatureVisitor mySignatureWithGenericBoundUsageCrawler = new BaseSignatureVisitor() {
    @Override
    public void visitClassType(String name) {
      super.visitClassType(name);
      addUsage(new ClassAsGenericBoundUsage(name));
    }
  };

  private boolean myIsModule = false;
  private final String myFileName;
  private final boolean myIsGenerated;
  private final boolean myIsLibraryMode;
  private int myAccess;
  private String myName;
  private String myVersion; // for class contains a class bytecode version, for module contains a module version
  private String mySuperClass;
  private Iterable<String> myInterfaces;
  private String mySignature;

  private final Ref<String> myClassNameHolder = Ref.create();
  private final Ref<String> myOuterClassName = Ref.create();
  private final Ref<Boolean> myLocalClassFlag = Ref.create(false);
  private final Ref<Boolean> myAnonymousClassFlag = Ref.create(false);
  private final Ref<Boolean> mySealedClassFlag = Ref.create(false);

  private final List<JvmMethod> myMethods = new ArrayList<>();
  private final List<JvmField> myFields = new ArrayList<>();
  private final Set<Usage> myUsages = new HashSet<>();
  private final Set<ElemType> myTargets = EnumSet.noneOf(ElemType.class);
  private RetentionPolicy myRetentionPolicy = null;

  private final Map<TypeRepr.ClassType, Set<String>> myAnnotationArguments = new HashMap<>();
  private final Map<TypeRepr.ClassType, Set<ElemType>> myAnnotationTargets = new HashMap<>();
  private final Set<ElementAnnotation> myAnnotations = new HashSet<>();

  private final Set<ModuleRequires> myModuleRequires = new HashSet<>();
  private final Set<ModulePackage> myModuleExports = new HashSet<>();

  private final List<JvmMetadata<?, ?>> myMetadata = new ArrayList<>(2);

  private JvmClassNodeBuilder(final String fn, boolean isGenerated, boolean isLibraryMode) {
    super(ASM_API_VERSION);
    myFileName = fn;
    myIsGenerated = isGenerated;
    myIsLibraryMode = isLibraryMode;
  }

  public static JvmClassNodeBuilder create(String filePath, ClassReader cr, boolean isGenerated) {
    JvmClassNodeBuilder builder = new JvmClassNodeBuilder(filePath, isGenerated, false);
    try {
      cr.accept(builder, ClassReader.SKIP_FRAMES);
    }
    catch (RuntimeException e) {
      throw new RuntimeException("Corrupted .class file: " + filePath, e);
    }
    return builder;
  }

  public static JvmClassNodeBuilder createForLibrary(String filePath, ClassReader cr) {
    JvmClassNodeBuilder builder = new JvmClassNodeBuilder(filePath, false, true);
    try {
      cr.accept(builder, ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
    }
    catch (RuntimeException e) {
      throw new RuntimeException("Corrupted .class file: " + filePath, e);
    }
    return builder;
  }

  @Override
  public @NotNull JvmNodeReferenceID getReferenceID() {
    return new JvmNodeReferenceID(myName);
  }

  @Override
  public void addUsage(Usage usage) {
    ReferenceID owner = usage.getElementOwner();
    if (!(owner instanceof JvmNodeReferenceID) || !JvmClass.OBJECT_CLASS_NAME.equals(((JvmNodeReferenceID)owner).getNodeName())) {
      myUsages.add(usage);
    }
  }

  @Override
  public JVMClassNode<? extends JVMClassNode<?, ?>, ? extends Proto.Diff<? extends JVMClassNode<?, ?>>> getResult() {
    JVMFlags flags = new JVMFlags(myAccess);
    if (myLocalClassFlag.get()) {
      flags = flags.deriveIsLocal();
    }
    if (myAnonymousClassFlag.get()) {
      flags = flags.deriveIsAnonymous();
    }
    if (mySealedClassFlag.get()) {
      flags = flags.deriveIsSealed();
    }
    if (myIsGenerated) {
      flags = flags.deriveIsGenerated();
    }
    if (myIsLibraryMode) {
      flags = flags.deriveIsLibrary();
    }

    if (myIsModule) {
      if (!myIsLibraryMode) {
        for (ModuleUsage usage : Iterators.map(Iterators.filter(myModuleRequires, r -> !Objects.equals(myName, r.getName())), r -> new ModuleUsage(r.getName()))) {
          addUsage(usage);
        }
      }
      return new JvmModule(flags, myName, myFileName, myVersion, myModuleRequires, myModuleExports, myIsLibraryMode? Set.of() : myUsages, myMetadata);
    }

    if (!myIsLibraryMode) {
      for (Usage usage : Iterators.flat(new TypeRepr.ClassType(mySuperClass).getUsages(), Iterators.flat(Iterators.map(myInterfaces, s -> new TypeRepr.ClassType(s).getUsages())))) {
        addUsage(usage);
      }
      for (Usage usage : Iterators.flat(Iterators.map(myFields, f -> f.getType().getUsages()))) {
        addUsage(usage);
      }
      for (JvmMethod jvmMethod : myMethods) {
        for (Usage usage : jvmMethod.getType().getUsages()) {
          addUsage(usage);
        }
        for (Usage usage : Iterators.flat(Iterators.map(jvmMethod.getArgTypes(), TypeRepr::getUsages))) {
          addUsage(usage);
        }
        for (Usage usage : Iterators.flat(Iterators.map(jvmMethod.getExceptions(), TypeRepr.ClassType::getUsages))) {
          addUsage(usage);
        }
      }
    }

    Iterators.BooleanFunction<? super Proto> visibilityFilter = proto -> proto.isPublic() || proto.isProtected();
    var fields = myIsLibraryMode? Iterators.filter(myFields, visibilityFilter) : myFields;
    var methods = myIsLibraryMode? Iterators.filter(myMethods, visibilityFilter) : myMethods;
    var usages = myIsLibraryMode? Set.<Usage>of() : myUsages;
    return new JvmClass(
      flags, mySignature, myName, myFileName, mySuperClass, myOuterClassName.get(), myInterfaces, fields, methods, myAnnotations, myTargets, myRetentionPolicy, usages, myMetadata
    );
  }

  @Override
  public void visit(int version, int access, String name, String sig, String superName, String[] interfaces) {
    myAccess = access;
    myName = name;
    myUsages.add(new ImportPackageOnDemandUsage(JvmClass.getPackageName(name))); // implicit 'import' of the package to which the node belongs to
    myVersion = String.valueOf(version);
    mySignature = sig;
    mySuperClass = superName;
    myInterfaces = Iterators.asIterable(interfaces);

    myClassNameHolder.set(name);
    processSignature(sig);
  }

  @Override
  public void visitEnd() {
    for (Map.Entry<TypeRepr.ClassType, Set<ElemType>> entry : myAnnotationTargets.entrySet()) {
      TypeRepr.ClassType type = entry.getKey();
      Set<ElemType> targets = entry.getValue();
      Set<String> usedArguments = myAnnotationArguments.get(type);
      addUsage(new AnnotationUsage(type, usedArguments != null? usedArguments : Collections.emptyList(), targets));
    }
  }

  @Override
  public ModuleVisitor visitModule(String name, int access, String version) {
    myIsModule = true;
    myAccess = access;
    myName = name;
    myVersion = version;
    return new ModuleCrawler();
  }

  @Override
  public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
    if ("Ljava/lang/annotation/Target;".equals(desc)) {
      return new AnnotationTargetCrawler();
    }

    if ("Ljava/lang/annotation/Retention;".equals(desc)) {
      return new AnnotationRetentionPolicyCrawler();
    }

    if ("Lkotlin/Metadata;".equals(desc)) {
      return new KotlinMetadataCrawler(myMetadata::add);
    }

    return new AnnotationCrawler((TypeRepr.ClassType)TypeRepr.getType(desc), (myAccess & Opcodes.ACC_ANNOTATION) > 0? ElemType.ANNOTATION_TYPE : ElemType.TYPE, res-> myAnnotations.add(res));
  }

  @Override
  public void visitSource(String source, String debug) {
  }

  @Override
  public FieldVisitor visitField(final int access, final String name, final String desc, final String signature, final Object value) {
    processSignature(signature);

    return new FieldVisitor(ASM_API_VERSION) {
      final Set<ElementAnnotation> annotations = new HashSet<>();

      @Override
      public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        return new AnnotationCrawler((TypeRepr.ClassType)TypeRepr.getType(desc), ElemType.FIELD, res -> annotations.add(res));
      }

      @Override
      public void visitEnd() {
        try {
          super.visitEnd();
        }
        finally {
          if ((access & Opcodes.ACC_SYNTHETIC) == 0 || (access & Opcodes.ACC_PRIVATE) == 0) {
            myFields.add(new JvmField(new JVMFlags(access), signature, name, desc, annotations, value));
          }
        }
      }
    };
  }

  private boolean isInlined(String methodName, String methodDescriptor) {
    KmDeclarationContainer container = findKotlinDeclarationContainer();
    if (container != null) {
      JvmMethodSignature sig = new JvmMethodSignature(methodName, methodDescriptor);
      for (KmFunction f : container.getFunctions()) {
        if (Attributes.isInline(f) && sig.equals(JvmExtensionsKt.getSignature(f))) {
          return true;
        }
      }
      for (KmProperty p : container.getProperties()) {
        if (Attributes.isInline(p.getGetter()) && sig.equals(JvmExtensionsKt.getGetterSignature(p))) {
          return true;
        }
        var setter = p.getSetter();
        if (setter != null && Attributes.isInline(setter) && sig.equals(JvmExtensionsKt.getSetterSignature(p))) {
          return true;
        }
      }
    }
    return false;
  }

  private KmDeclarationContainer findKotlinDeclarationContainer() {
    KotlinMeta meta = (KotlinMeta)Iterators.find(myMetadata, md-> md instanceof KotlinMeta);
    return meta == null ? null : meta.getDeclarationContainer();
  }

  @Override
  public MethodVisitor visitMethod(final int access, final String n, final String desc, final String signature, final String[] exceptions) {
    final Ref<Object> defaultValue = Ref.create();
    final Set<ElementAnnotation> annotations = new HashSet<>();
    final Set<ParamAnnotation> paramAnnotations = new HashSet<>();
    processSignature(signature);

    boolean isInlined = isInlined(n, desc);
    Textifier printer = new Textifier();

    MethodVisitor visitor = new MethodVisitor(ASM_API_VERSION) {

      @Override
      public void visitEnd() {
        if ((access & Opcodes.ACC_SYNTHETIC) == 0 || (access & Opcodes.ACC_BRIDGE) > 0 || (access & Opcodes.ACC_PRIVATE) == 0) {
          if (isInlined) {
            // use 'defaultValue' attribute to store the hash of the function body to track changes in inline method implementation
            defaultValue.set(buildContentHash(ContentHashBuilder.create(), printer.getText()).getResult());
          }
          myMethods.add(new JvmMethod(new JVMFlags(access), signature, n, desc, annotations, paramAnnotations, Iterators.asIterable(exceptions), defaultValue.get()));
        }
      }

      private ContentHashBuilder buildContentHash(ContentHashBuilder builder, Iterable<?> dataSequence) {
        for (Object o : dataSequence) {
          if (o instanceof Iterable) {
            buildContentHash(builder, (Iterable<?>)o);
          }
          else {
            builder.update(o);
          }
        }
        return builder;
      }

      @Override
      public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        return new AnnotationCrawler((TypeRepr.ClassType)TypeRepr.getType(desc), "<init>".equals(n)? ElemType.CONSTRUCTOR : ElemType.METHOD, res -> annotations.add(res));
      }

      @Override
      public AnnotationVisitor visitAnnotationDefault() {
        return new AnnotationVisitor(ASM_API_VERSION) {
          private @Nullable List<Object> myAcc;

          @Override
          public void visit(String name, Object value) {
            collectValue(value);
          }

          @Override
          public void visitEnum(String name, String desc, String value) {
            collectValue(value);
          }

          @Override
          public AnnotationVisitor visitArray(String name) {
            myAcc = new ArrayList<>();
            return this;
          }

          @Override
          public void visitEnd() {
            if (myAcc != null) {
              if (!myAcc.isEmpty()) {
                Object elem = null;
                for (Object o : myAcc) {
                  if (o != null) {
                    elem = o;
                    break;
                  }
                }
                if (elem != null) {
                  defaultValue.set(myAcc.toArray((Object[])Array.newInstance(elem.getClass(), 0)));
                }
              }

              if (defaultValue.get() == null) {
                Type declaredType = Type.getReturnType(desc);
                // array of array is not allowed by spec
                defaultValue.set(Array.newInstance(getTypeClass(declaredType.getSort() == Type.ARRAY? declaredType.getElementType() : declaredType), myAcc.size()));
              }
            }
          }

          private void collectValue(Object value) {
            if (myAcc != null) {
              myAcc.add(value);
            }
            else {
              defaultValue.set(value);
            }
          }
        };
      }

      @Override
      public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
        return new AnnotationCrawler(
          (TypeRepr.ClassType)TypeRepr.getType(desc),
          ElemType.PARAMETER,
          res -> paramAnnotations.add(new ParamAnnotation(parameter, res.getAnnotationClass(), res.getContentHash()))
        );
      }

      @Override
      public void visitLdcInsn(Object cst) {
        if (cst instanceof Type) {
          addUsage(new ClassUsage(((Type)cst).getInternalName()));
        }

        super.visitLdcInsn(cst);
      }

      @Override
      public void visitMultiANewArrayInsn(String desc, int dims) {
        final TypeRepr.ArrayType typ = (TypeRepr.ArrayType)TypeRepr.getType(desc);
        Iterators.collect(typ.getUsages(), myUsages);

        final TypeRepr element = typ.getDeepElementType();
        if (element instanceof TypeRepr.ClassType) {
          addUsage(new ClassNewUsage(((TypeRepr.ClassType)element).getJvmName()));
        }

        super.visitMultiANewArrayInsn(desc, dims);
      }

      @Override
      public void visitLocalVariable(String n, String desc, String signature, Label start, Label end, int index) {
        if (!"this".equals(n)) {
          processSignature(signature);
          Iterators.collect(TypeRepr.getType(desc).getUsages(), myUsages);
        }
        super.visitLocalVariable(n, desc, signature, start, end, index);
      }

      @Override
      public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        if (type != null) {
          Iterators.collect(new TypeRepr.ClassType(type).getUsages(), myUsages);
        }
        super.visitTryCatchBlock(start, end, handler, type);
      }

      @Override
      public void visitTypeInsn(int opcode, String type) {
        final TypeRepr typ = type.startsWith("[")? TypeRepr.getType(type) : new TypeRepr.ClassType(type);

        if (opcode == Opcodes.NEW) {
          addUsage(new ClassUsage(((TypeRepr.ClassType)typ).getJvmName()));
          addUsage(new ClassNewUsage(((TypeRepr.ClassType)typ).getJvmName()));
          final int ktLambdaMarker = type.indexOf(KOTLIN_LAMBDA_USAGE_CLASS_MARKER);
          if (ktLambdaMarker > 0) {
            final int ifNameStart = ktLambdaMarker + KOTLIN_LAMBDA_USAGE_CLASS_MARKER.length();
            final int ifNameEnd = type.indexOf("$", ifNameStart);
            if (ifNameEnd > ifNameStart) {
              addUsage(new ClassNewUsage(type.substring(ifNameStart, ifNameEnd).replace('_', '/')));
            }
          }
        }
        else if (opcode == Opcodes.ANEWARRAY) {
          if (typ instanceof TypeRepr.ClassType) {
            addUsage(new ClassUsage(((TypeRepr.ClassType)typ).getJvmName()));
            addUsage(new ClassNewUsage(((TypeRepr.ClassType)typ).getJvmName()));
          }
        }

        Iterators.collect(typ.getUsages(), myUsages);

        super.visitTypeInsn(opcode, type);
      }

      @Override
      public void visitFieldInsn(int opcode, String owner, String name, String desc) {
        registerFieldUsage(opcode, owner, name, desc);
        super.visitFieldInsn(opcode, owner, name, desc);
      }

      @Override
      public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        registerMethodUsage(owner, name, desc);
        super.visitMethodInsn(opcode, owner, name, desc, itf);
      }

      @Override
      public void visitInvokeDynamicInsn(String methodName, String desc, Handle bsm, Object... bsmArgs) {
        final Type returnType = Type.getReturnType(desc);
        Iterators.collect(TypeRepr.getType(returnType).getUsages(), myUsages);

        // common args processing
        for (Object arg : bsmArgs) {
          if (arg instanceof Type) {
            final Type type = (Type)arg;
            if (type.getSort() == Type.METHOD) {
              for (Type argType : type.getArgumentTypes()) {
                Iterators.collect(TypeRepr.getType(argType).getUsages(), myUsages);
              }
              Iterators.collect(TypeRepr.getType(type.getReturnType()).getUsages(), myUsages);
            }
            else {
              Iterators.collect(TypeRepr.getType(type).getUsages(), myUsages);
            }
          }
          else if (arg instanceof Handle) {
            processMethodHandle((Handle)arg);
          }
        }

        if (LAMBDA_FACTORY_CLASS.equals(bsm.getOwner())) {
          // This invokeDynamic implements a lambda or method reference usage.
          // Need to register method usage for the corresponding SAM type.
          // The first three arguments to the bootstrap methods are provided automatically by VM.
          // Arguments in an args array are expected to be as following:
          // [0]: Type: Signature and return type of method to be implemented by the function object.
          // [1]: Handle: implementation method handle
          // [2]: Type: The signature and return type that should be enforced dynamically at invocation time.
          // Maybe the same as samMethodType, or may be a specialization of it
          // [...]: optional additional arguments

          if (returnType.getSort() == Type.OBJECT && bsmArgs.length >= 3) {
            if (bsmArgs[0] instanceof Type) {
              final Type samMethodType = (Type)bsmArgs[0];
              if (samMethodType.getSort() == Type.METHOD) {
                registerMethodUsage(returnType.getInternalName(), methodName, samMethodType.getDescriptor());
                // reflect dynamic proxy instantiation with NewClassUsage
                addUsage(new ClassNewUsage(returnType.getInternalName()));
              }
            }
          }
        }

        super.visitInvokeDynamicInsn(methodName, desc, bsm, bsmArgs);
      }

      private void processMethodHandle(Handle handle) {
        final String memberOwner = handle.getOwner();
        if (memberOwner != null && !memberOwner.equals(myClassNameHolder.get())) {
          // do not register access to own class members
          final String memberName = handle.getName();
          final String memberDescriptor = handle.getDesc();
          final int opCode = getFieldAccessOpcode(handle);
          if (opCode > 0) {
            registerFieldUsage(opCode, memberOwner, memberName, memberDescriptor);
          }
          else {
            registerMethodUsage(memberOwner, memberName, memberDescriptor);
          }
        }
      }

      private void registerFieldUsage(int opcode, String owner, String fName, String desc) {
        if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
          addUsage(new FieldAssignUsage(owner, fName, desc));
        }
        if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) {
          Iterators.collect(TypeRepr.getType(desc).getUsages(), myUsages);
        }
        addUsage(new FieldUsage(owner, fName, desc));
      }

      private void registerMethodUsage(String owner, String name, @Nullable String desc) {
        //myUsages.add(UsageRepr.createMetaMethodUsage(myContext, methodName, methodOwner));
        if (desc != null) {
          addUsage(new MethodUsage(owner, name, desc));
          Iterators.collect(TypeRepr.getType(Type.getReturnType(desc)).getUsages(), myUsages);
        }
        else {
          // todo: verify for which methods null descriptor is passed
          addUsage(new MethodUsage(owner, name, ""));
        }
      }

    };

    return isInlined? new TraceMethodVisitor(visitor, printer) : visitor;
  }

  /**
   * @return corresponding field access opcode or -1 if the handle does not represent a field access handle
   */
  private static int getFieldAccessOpcode(Handle handle) {
    switch (handle.getTag()) {
      case Opcodes.H_GETFIELD:
        return Opcodes.GETFIELD;
      case Opcodes.H_GETSTATIC:
        return Opcodes.GETSTATIC;
      case Opcodes.H_PUTFIELD:
        return Opcodes.PUTFIELD;
      case Opcodes.H_PUTSTATIC:
        return Opcodes.PUTSTATIC;
      default:
        return -1;
    }
  }

  @Override
  public void visitInnerClass(String name, String outerName, String innerName, int access) {
    if (name != null && name.equals(myClassNameHolder.get())) {
      // set outer class name only if we are parsing the real inner class and
      // not the reference to inner class inside some top-level class
      myAccess |= access; // information about some access flags for the inner class is missing from the mask passed to 'visit' method
      if (outerName != null) {
        myOuterClassName.set(outerName);
      }
      if (innerName == null) {
        myAnonymousClassFlag.set(true);
      }
    }
  }

  @Override
  public void visitOuterClass(final String owner, final String name, final String desc) {
    myOuterClassName.set(owner);

    if (name != null) {
      myLocalClassFlag.set(true);
    }
  }

  @Override
  public void visitPermittedSubclass(String permittedSubclass) {
    mySealedClassFlag.set(true);
    addUsage(new ClassUsage(permittedSubclass));
    addUsage(new ClassPermitsUsage(permittedSubclass));
  }

  private static boolean isAnnotationTracked(TypeRepr.ClassType annotationType) {
    for (JvmDifferentiateStrategy strategy : ourDifferentiateStrategies) {
      if (strategy.isAnnotationTracked(annotationType)) {
        return true;
      }
    }
    return false;
  }

  private class BaseSignatureVisitor extends SignatureVisitor {

    BaseSignatureVisitor() {
      super(ASM_API_VERSION);
    }

    @Override
    public void visitClassType(String name) {
      addUsage(new ClassUsage(name));
    }
  }

  private interface ContentHashBuilder {
    ContentHashBuilder NULL = new ContentHashBuilder() {
      @Override
      public void update(Object data) {
      }

      @Override
      public Object getResult() {
        return null;
      }
    };

    void update(Object data);
    Object getResult();

    static ContentHashBuilder create() {
      HashStream64 digest = Hashing.komihash5_0().hashStream();
      return wrap(new ContentHashBuilder() {
        @Override
        public void update(Object data) {
          if (data != null && data.getClass().isArray()) {
            for (int idx = 0, length = Array.getLength(data); idx < length; idx++) {
              update(Array.get(data, idx));
            }
          }
          else {
            digest.putString(String.valueOf(data).trim());
          }
        }

        @Override
        public Object getResult() {
          return digest.getAsLong();
        }
      });
    }

    static ContentHashBuilder wrap(ContentHashBuilder delegate) {
      return new ContentHashBuilder() {
        boolean hasData = false;
        @Override
        public void update(Object data) {
          hasData = true;
          delegate.update(data);
        }

        @Override
        public Object getResult() {
          return hasData? delegate.getResult() : null;
        }
      };
    }
  }

  private static Class getTypeClass(Type t)  {
    switch(t.getSort()) {
      case Type.BOOLEAN: return boolean.class;
      case Type.CHAR: return char.class;
      case Type.BYTE: return byte.class;
      case Type.SHORT: return short.class;
      case Type.INT: return int.class;
      case Type.FLOAT: return float.class;
      case Type.LONG: return long.class;
      case Type.DOUBLE: return double.class;
      case Type.OBJECT:
        if ("java.lang.String".equals(t.getClassName())) {
          return String.class;
        }
    }
    return Type.class;
  }

}
