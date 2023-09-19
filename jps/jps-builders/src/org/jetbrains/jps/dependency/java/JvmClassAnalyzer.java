// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.javac.Iterators;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.signature.SignatureReader;
import org.jetbrains.org.objectweb.asm.signature.SignatureVisitor;

import java.lang.annotation.RetentionPolicy;
import java.util.*;

final class JvmClassAnalyzer {
  private final static Logger LOG = Logger.getInstance(JvmClassAnalyzer.class);
  public static final String LAMBDA_FACTORY_CLASS = "java/lang/invoke/LambdaMetafactory";
  private static final String KOTLIN_LAMBDA_USAGE_CLASS_MARKER = "$sam$";
  private static final int ASM_API_VERSION = Opcodes.API_VERSION;


  JvmClassAnalyzer() {
  }

  private static class ClassCrawler extends ClassVisitor {
    private final class AnnotationRetentionPolicyCrawler extends AnnotationVisitor {
      private AnnotationRetentionPolicyCrawler() {
        super(ASM_API_VERSION);
      }

      @Override
      public void visit(String name, Object value) { }

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
      public void visitEnd() { }
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

      private final Set<String> myUsedArguments = new HashSet<>();

      private AnnotationCrawler(final TypeRepr.ClassType type, final ElemType target) {
        super(ASM_API_VERSION);
        this.myType = type;
        this.myTarget = target;
        final Set<ElemType> targets = myAnnotationTargets.get(type);
        if (targets == null) {
          myAnnotationTargets.put(type, EnumSet.of(target));
        }
        else {
          targets.add(target);
        }
        myUsages.add(new ClassUsage(type.getJvmName()));
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
              descriptor.append("I;");
              break;
            case "java/lang/Short":
              descriptor.append("S;");
              break;
            case "java/lang/Long":
              descriptor.append("J;");
              break;
            case "java/lang/Byte":
              descriptor.append("B;");
              break;
            case "java/lang/Char":
              descriptor.append("C;");
              break;
            case "java/lang/Boolean":
              descriptor.append("Z;");
              break;
            case "java/lang/Float":
              descriptor.append("F;");
              break;
            case "java/lang/Double":
              descriptor.append("D;");
              break;
            default:
              descriptor.append("L").append(name).append(";");
              break;
          }
        }
        return descriptor.toString();
      }

      @Nullable
      private String myArrayName;

      @Override
      public void visit(String name, Object value) {
        final boolean isArray = name == null && myArrayName != null;
        final String argName;
        if (name != null) {
          argName = name;
        }
        else {
          argName = myArrayName;
          // not interested in collecting complete array value; need to know just array type
          myArrayName = null;
        }
        if (argName != null) {
          registerUsages(argName, getMethodDescr(value, isArray), value);
        }
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
          // not interested in collecting complete array value; need to know just array type
          myArrayName = null;
        }
        if (argName != null) {
          registerUsages(argName, (isArray? "()[" : "()") + desc, value);
        }
      }

      @Override
      public AnnotationVisitor visitAnnotation(String name, String desc) {
        return new AnnotationCrawler((TypeRepr.ClassType)TypeRepr.getType(desc), myTarget);
      }

      @Override
      public AnnotationVisitor visitArray(String name) {
        myArrayName = name;
        return this;
      }

      private void registerUsages(String methodName, String methodDescr, Object value) {

        if (value instanceof Type) {
          final String className = ((Type)value).getClassName().replace('.', '/');
          myUsages.add(new ClassUsage(className));
        }

        myUsages.add(new MethodUsage(myType.getJvmName(), methodName, methodDescr));
        //myUsages.add(UsageRepr.createMetaMethodUsage(myContext, methodName, myType.className));

        myUsedArguments.add(methodName);
      }

      @Override
      public void visitEnd() {
        Set<String> s = myAnnotationArguments.get(myType);
        if (s == null) {
          myAnnotationArguments.put(myType, myUsedArguments);
        }
        else {
          s.retainAll(myUsedArguments);
        }
      }
    }

    private class ModuleCrawler extends ModuleVisitor {
      ModuleCrawler() {
        super(ASM_API_VERSION);
      }

      @Override
      public void visitMainClass(String mainClass) {
        myUsages.add(new ClassUsage(mainClass));
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
        myUsages.add(new ClassUsage(service));
      }

      @Override
      public void visitProvide(String service, String... providers) {
        myUsages.add(new ClassUsage(service));
        if (providers != null) {
          for (String provider : providers) {
            myUsages.add(new ClassUsage(provider));
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
          LOG.info("Problems parsing signature \"" + sig + "\" in " + myFileName, e);
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
        return wildcard == '+' || wildcard == '-' ? mySignatureWithGenericBoundUsageCrawler : super.visitTypeArgument(wildcard);
      }
    };

    private final SignatureVisitor mySignatureWithGenericBoundUsageCrawler = new BaseSignatureVisitor() {
      @Override
      public void visitClassType(String name) {
        myUsages.add(new ClassUsage(name));
        myUsages.add(new ClassAsGenericBoundUsage(name));
      }
    };

    private boolean myTakeIntoAccount = false;
    private boolean myIsModule = false;
    private final String myFileName;
    private final boolean myIsGenerated;
    private int myAccess;
    private String myName;
    private String myVersion; // for class contains a class bytecode version, for module contains a module version
    private String mySuperClass;
    private String[] myInterfaces;
    private String mySignature;

    private final Ref<String> myClassNameHolder = Ref.create();
    private final Ref<String> myOuterClassName = Ref.create();
    private final Ref<Boolean> myLocalClassFlag = Ref.create(false);
    private final Ref<Boolean> myAnonymousClassFlag = Ref.create(false);

    private final Set<JvmMethod> myMethods = new HashSet<>();
    private final Set<JvmField> myFields = new HashSet<>();
    private final Set<Usage> myUsages = new HashSet<>();
    private final Set<ElemType> myTargets = EnumSet.noneOf(ElemType.class);
    private RetentionPolicy myRetentionPolicy = null;

    private final Map<TypeRepr.ClassType, Set<String>> myAnnotationArguments = new HashMap<>();
    private final Map<TypeRepr.ClassType, Set<ElemType>> myAnnotationTargets = new HashMap<>();
    private final Set<TypeRepr.ClassType> myAnnotations = new HashSet<>();

    private final Set<ModuleRequires> myModuleRequires = new HashSet<>();
    private final Set<ModulePackage> myModuleExports = new HashSet<>();

    ClassCrawler(final String fn, boolean isGenerated) {
      super(ASM_API_VERSION);
      myFileName = fn;
      myIsGenerated = isGenerated;
    }

    private boolean notPrivate(final int access) {
      return (access & Opcodes.ACC_PRIVATE) == 0;
    }

    public JVMClassNode getResult() {
      if (!myTakeIntoAccount) {
        return null;
      }
      JVMFlags flags = new JVMFlags(myAccess);
      if (myLocalClassFlag.get()) {
        flags = flags.deriveIsLocal();
      }
      if (myAnonymousClassFlag.get()) {
        flags = flags.deriveIsAnonymous();
      }
      if (myIsGenerated) {
        flags = flags.deriveIsGenerated();
      }
      if (myIsModule) {
        return new JvmModule(flags, myVersion, myFileName, myName, myModuleRequires, myModuleExports, myUsages);
      }
      return new JvmClass(flags, mySignature, myName, myFileName, mySuperClass, myOuterClassName.get(), Arrays.asList(myInterfaces), myFields, myMethods, myAnnotations, myTargets, myRetentionPolicy, myUsages);
    }

    @Override
    public void visit(int version, int access, String name, String sig, String superName, String[] interfaces) {
      myTakeIntoAccount = notPrivate(access);

      myAccess = access;
      myName = name;
      myVersion = String.valueOf(version);
      mySignature = sig;
      mySuperClass = superName;
      myInterfaces = interfaces;

      myClassNameHolder.set(name);

      if (mySuperClass != null) {
        myUsages.add(new ClassUsage(mySuperClass));
      }

      if (myInterfaces != null) {
        for (String ifaceName : myInterfaces) {
          myUsages.add(new ClassUsage(ifaceName));
        }
      }

      processSignature(sig);
    }

    @Override
    public void visitEnd() {
      for (Map.Entry<TypeRepr.ClassType, Set<ElemType>> entry : myAnnotationTargets.entrySet()) {
        TypeRepr.ClassType type = entry.getKey();
        Set<ElemType> targets = entry.getValue();
        Set<String> usedArguments = myAnnotationArguments.get(type);
        myUsages.add(new AnnotationUsage(type, usedArguments != null? usedArguments : Collections.emptyList(), targets));
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
      if (desc.equals("Ljava/lang/annotation/Target;")) {
        return new AnnotationTargetCrawler();
      }

      if (desc.equals("Ljava/lang/annotation/Retention;")) {
        return new AnnotationRetentionPolicyCrawler();
      }

      TypeRepr.ClassType annotationType = (TypeRepr.ClassType)TypeRepr.getType(desc);
      myAnnotations.add(annotationType);
      return new AnnotationCrawler(annotationType, (myAccess & Opcodes.ACC_ANNOTATION) > 0 ? ElemType.ANNOTATION_TYPE : ElemType.TYPE);
    }

    @Override
    public void visitSource(String source, String debug) {
    }

    @Override
    public FieldVisitor visitField(final int access, final String name, final String desc, final String signature, final Object value) {
      processSignature(signature);

      return new FieldVisitor(ASM_API_VERSION) {
        final Set<TypeRepr.ClassType> annotations = new HashSet<>();

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
          final TypeRepr.ClassType annotation = (TypeRepr.ClassType)TypeRepr.getType(desc);
          annotations.add(annotation);
          return new AnnotationCrawler(annotation, ElemType.FIELD);
        }

        @Override
        public void visitEnd() {
          try {
            super.visitEnd();
          }
          finally {
            if ((access & Opcodes.ACC_SYNTHETIC) == 0) {
              myFields.add(new JvmField(new JVMFlags(access), signature, name, desc, annotations, value));
            }
          }
        }
      };
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String n, final String desc, final String signature, final String[] exceptions) {
      final Ref<Object> defaultValue = Ref.create();
      final Set<TypeRepr.ClassType> annotations = new HashSet<>();
      final Set<ParamAnnotation> paramAnnotations = new HashSet<>();
      processSignature(signature);

      return new MethodVisitor(ASM_API_VERSION) {
        @Override
        public void visitEnd() {
          if ((access & Opcodes.ACC_SYNTHETIC) == 0 || (access & Opcodes.ACC_BRIDGE) > 0) {
            myMethods.add(new JvmMethod(new JVMFlags(access), signature, n, desc, annotations, paramAnnotations, exceptions, defaultValue.get()));
          }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
          final TypeRepr.ClassType annoType = (TypeRepr.ClassType)TypeRepr.getType(desc);
          annotations.add(annoType);
          return new AnnotationCrawler(annoType, "<init>".equals(n) ? ElemType.CONSTRUCTOR : ElemType.METHOD);
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
              myAcc = new SmartList<>();
              return this;
            }

            @Override
            public void visitEnd() {
              if (myAcc != null) {
                Object[] template = null;
                if (!myAcc.isEmpty()) {
                  final Object elem = myAcc.get(0);
                  if (elem != null) {
                    template = ArrayUtil.newArray(elem.getClass(), 0);
                  }
                }
                defaultValue.set(template != null? myAcc.toArray(template) : myAcc.toArray());
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
          final TypeRepr.ClassType annoType = (TypeRepr.ClassType)TypeRepr.getType(desc);
          paramAnnotations.add(new ParamAnnotation(parameter, annoType));
          return new AnnotationCrawler(annoType, ElemType.PARAMETER);
        }

        @Override
        public void visitLdcInsn(Object cst) {
          if (cst instanceof Type) {
            myUsages.add(new ClassUsage(((Type)cst).getInternalName()));
          }

          super.visitLdcInsn(cst);
        }

        @Override
        public void visitMultiANewArrayInsn(String desc, int dims) {
          final TypeRepr.ArrayType typ = (TypeRepr.ArrayType)TypeRepr.getType(desc);
          Iterators.collect(typ.getUsages(), myUsages);

          final TypeRepr element = typ.getDeepElementType();
          if (element instanceof TypeRepr.ClassType) {
            myUsages.add(new ClassNewUsage(((TypeRepr.ClassType)element).getJvmName()));
          }

          super.visitMultiANewArrayInsn(desc, dims);
        }

        @Override
        public void visitLocalVariable(String n, String desc, String signature, Label start, Label end, int index) {
          processSignature(signature);
          Iterators.collect(TypeRepr.getType(desc).getUsages(), myUsages);
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
            myUsages.add(new ClassUsage(((TypeRepr.ClassType)typ).getJvmName()));
            myUsages.add(new ClassNewUsage(((TypeRepr.ClassType)typ).getJvmName()));
            final int ktLambdaMarker = type.indexOf(KOTLIN_LAMBDA_USAGE_CLASS_MARKER);
            if (ktLambdaMarker > 0) {
              final int ifNameStart = ktLambdaMarker + KOTLIN_LAMBDA_USAGE_CLASS_MARKER.length();
              final int ifNameEnd = type.indexOf("$", ifNameStart);
              if (ifNameEnd > ifNameStart) {
                myUsages.add(new ClassNewUsage(type.substring(ifNameStart, ifNameEnd).replace('_', '/')));
              }
            }
          }
          else if (opcode == Opcodes.ANEWARRAY) {
            if (typ instanceof TypeRepr.ClassType) {
              myUsages.add(new ClassUsage(((TypeRepr.ClassType)typ).getJvmName()));
              myUsages.add(new ClassNewUsage(((TypeRepr.ClassType)typ).getJvmName()));
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
            // Need to register method usage for the corresponding SAM-type.
            // First three arguments to the bootstrap methods are provided automatically by VM.
            // Arguments in args array are expected to be as following:
            // [0]: Type: Signature and return type of method to be implemented by the function object.
            // [1]: Handle: implementation method handle
            // [2]: Type: The signature and return type that should be enforced dynamically at invocation time. May be the same as samMethodType, or may be a specialization of it
            // [...]: optional additional arguments

            if (returnType.getSort() == Type.OBJECT && bsmArgs.length >= 3) {
              if (bsmArgs[0] instanceof Type) {
                final Type samMethodType = (Type)bsmArgs[0];
                if (samMethodType.getSort() == Type.METHOD) {
                  registerMethodUsage(returnType.getInternalName(), methodName, samMethodType.getDescriptor());
                  // reflect dynamic proxy instantiation with NewClassUsage
                  myUsages.add(new ClassNewUsage(returnType.getInternalName()));
                }
              }
            }
          }

          super.visitInvokeDynamicInsn(methodName, desc, bsm, bsmArgs);
        }

        private void processMethodHandle(Handle handle) {
          final String memberOwner = handle.getOwner();
          if (memberOwner != null &&  !memberOwner.equals(myClassNameHolder.get())) {
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
            myUsages.add(new FieldAssignUsage(owner, fName, desc));
          }
          if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) {
            Iterators.collect(TypeRepr.getType(desc).getUsages(), myUsages);
          }
          myUsages.add(new FieldUsage(owner, fName, desc));
        }

        private void registerMethodUsage(String owner, String name, @Nullable String desc) {
          //myUsages.add(UsageRepr.createMetaMethodUsage(myContext, methodName, methodOwner));
          if (desc != null) {
            myUsages.add(new MethodUsage(owner, name, desc));
            Iterators.collect(TypeRepr.getType(Type.getReturnType(desc)).getUsages(), myUsages);
          }
          else {
            // todo: verify for which methods null descriptor is passed
            myUsages.add(new MethodUsage(owner, name, ""));
          }
        }

      };
    }

    /**
     * @return corresponding field access opcode or -1 if the handle does not represent field access handle
     */
    private int getFieldAccessOpcode(Handle handle) {
      switch (handle.getTag()) {
        case Opcodes.H_GETFIELD: return Opcodes.GETFIELD;
        case Opcodes.H_GETSTATIC: return Opcodes.GETSTATIC;
        case Opcodes.H_PUTFIELD: return Opcodes.PUTFIELD;
        case Opcodes.H_PUTSTATIC: return Opcodes.PUTSTATIC;
        default: return -1;
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

    private class BaseSignatureVisitor extends SignatureVisitor {
      BaseSignatureVisitor() {
        super(ASM_API_VERSION);
      }

      @Override
      public void visitFormalTypeParameter(String name) { }

      @Override
      public SignatureVisitor visitClassBound() {
        return super.visitClassBound();
      }

      @Override
      public SignatureVisitor visitInterfaceBound() {
        return super.visitInterfaceBound();
      }

      @Override
      public SignatureVisitor visitSuperclass() {
        return super.visitSuperclass();
      }

      @Override
      public SignatureVisitor visitInterface() {
        return super.visitInterface();
      }

      @Override
      public SignatureVisitor visitParameterType() {
        return super.visitParameterType();
      }

      @Override
      public SignatureVisitor visitReturnType() {
        return super.visitReturnType();
      }

      @Override
      public SignatureVisitor visitExceptionType() {
        return super.visitExceptionType();
      }

      @Override
      public void visitBaseType(char descriptor) {
      }

      @Override
      public void visitTypeVariable(String name) {
      }

      @Override
      public SignatureVisitor visitArrayType() {
        return super.visitArrayType();
      }

      @Override
      public void visitInnerClassType(String name) {
      }

      @Override
      public void visitTypeArgument() {
        super.visitTypeArgument();
      }

      @Override
      public SignatureVisitor visitTypeArgument(char wildcard) {
        return this;
      }

      @Override
      public void visitEnd() {
        super.visitEnd();
      }

      @Override
      public void visitClassType(String name) {
        myUsages.add(new ClassUsage(name));
      }
    }
  }

  public JVMClassNode analyze(String fileName, ClassReader cr, boolean isGenerated) {
    ClassCrawler visitor = new ClassCrawler(fileName, isGenerated);

    try {
      cr.accept(visitor, 0);
    }
    catch (RuntimeException e) {
      throw new RuntimeException("Corrupted .class file: " + fileName, e);
    }

    return visitor.getResult();
  }
}