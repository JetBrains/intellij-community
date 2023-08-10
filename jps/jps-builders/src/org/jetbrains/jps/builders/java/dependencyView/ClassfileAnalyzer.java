// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.signature.SignatureReader;
import org.jetbrains.org.objectweb.asm.signature.SignatureVisitor;

import java.lang.annotation.RetentionPolicy;
import java.util.*;

final class ClassfileAnalyzer {
  private final static Logger LOG = Logger.getInstance(ClassfileAnalyzer.class);
  public static final String LAMBDA_FACTORY_CLASS = "java/lang/invoke/LambdaMetafactory";
  private static final String KOTLIN_LAMBDA_USAGE_CLASS_MARKER = "$sam$";
  private static final int ASM_API_VERSION = Opcodes.API_VERSION;

  private final DependencyContext myContext;

  ClassfileAnalyzer(DependencyContext context) {
    this.myContext = context;
  }

  private class ClassCrawler extends ClassVisitor {
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
      public void visit(String name, Object value) { }

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
      public void visitEnd() { }
    }

    private final class AnnotationCrawler extends AnnotationVisitor {
      private final TypeRepr.ClassType myType;
      private final ElemType myTarget;

      private final IntOpenHashSet myUsedArguments = new IntOpenHashSet();

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
        myUsages.add(UsageRepr.createClassUsage(myContext, type.className));
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
        return new AnnotationCrawler((TypeRepr.ClassType)TypeRepr.getType(myContext, desc), myTarget);
      }

      @Override
      public AnnotationVisitor visitArray(String name) {
        myArrayName = name;
        return this;
      }

      private void registerUsages(String argName, String methodDescr, Object value) {
        final int methodName = myContext.get(argName);

        if (value instanceof Type) {
          final String className = ((Type)value).getClassName().replace('.', '/');
          myUsages.add(UsageRepr.createClassUsage(myContext, myContext.get(className)));
        }

        myUsages.add(UsageRepr.createMethodUsage(myContext, methodName, myType.className, methodDescr));
        myUsages.add(UsageRepr.createMetaMethodUsage(myContext, methodName, myType.className));

        myUsedArguments.add(methodName);
      }

      @Override
      public void visitEnd() {
        IntSet s = myAnnotationArguments.get(myType);
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
        myUsages.add(UsageRepr.createClassUsage(myContext, myContext.get(mainClass)));
      }

      @Override
      public void visitRequire(String module, int access, String version) {
        if (isExplicit(access)) {
          // collect non-synthetic dependencies only
          myModuleRequires.add(new ModuleRequiresRepr(myContext, access, myContext.get(module), version));
        }
      }

      @Override
      public void visitExport(String packaze, int access, String... modules) {
        if (isExplicit(access)) {
          // collect non-synthetic dependencies only
          myModuleExports.add(new ModulePackageRepr(myContext, myContext.get(packaze), modules != null? Arrays.asList(modules) : Collections.emptyList()));
        }
      }

      @Override
      public void visitUse(String service) {
        myUsages.add(UsageRepr.createClassUsage(myContext, myContext.get(service)));
      }

      @Override
      public void visitProvide(String service, String... providers) {
        myUsages.add(UsageRepr.createClassUsage(myContext, myContext.get(service)));
        if (providers != null) {
          for (String provider : providers) {
            myUsages.add(UsageRepr.createClassUsage(myContext, myContext.get(provider)));
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
          LOG.info("Problems parsing signature \"" + sig + "\" in " + myContext.getValue(myFileName), e);
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
        final int className = myContext.get(name);
        myUsages.add(UsageRepr.createClassUsage(myContext, className));
        myUsages.add(UsageRepr.createClassAsGenericBoundUsage(myContext, className));
      }
    };

    private boolean myTakeIntoAccount = false;
    private boolean myIsModule = false;
    private final int myFileName;
    private final boolean myIsGenerated;
    private int myAccess;
    private int myName;
    private int myVersion; // for class contains a class bytecode version, for module contains a module version
    private String mySuperClass;
    private String[] myInterfaces;
    private String mySignature;

    private final Ref<String> myClassNameHolder = Ref.create();
    private final Ref<String> myOuterClassName = Ref.create();
    private final Ref<Boolean> myLocalClassFlag = Ref.create(false);
    private final Ref<Boolean> myAnonymousClassFlag = Ref.create(false);

    private final Set<MethodRepr> myMethods = new HashSet<>();
    private final Set<FieldRepr> myFields = new HashSet<>();
    private final Set<UsageRepr.Usage> myUsages = new HashSet<>();
    private final Set<ElemType> myTargets = EnumSet.noneOf(ElemType.class);
    private RetentionPolicy myRetentionPolicy = null;

    private final Map<TypeRepr.ClassType, IntSet> myAnnotationArguments = new HashMap<>();
    private final Map<TypeRepr.ClassType, Set<ElemType>> myAnnotationTargets = new HashMap<>();
    private final Set<TypeRepr.ClassType> myAnnotations = new HashSet<>();

    private final Set<ModuleRequiresRepr> myModuleRequires = new HashSet<>();
    private final Set<ModulePackageRepr> myModuleExports = new HashSet<>();

    ClassCrawler(final int fn, boolean isGenerated) {
      super(ASM_API_VERSION);
      myFileName = fn;
      myIsGenerated = isGenerated;
    }

    private boolean notPrivate(final int access) {
      return (access & Opcodes.ACC_PRIVATE) == 0;
    }

    public ClassFileRepr getResult() {
      if (!myTakeIntoAccount) {
        return null;
      }
      if (myIsModule) {
        return new ModuleRepr(myContext, myAccess, myVersion, myFileName, myName, myModuleRequires, myModuleExports, myUsages);
      }
      return new ClassRepr(
        myContext, myAccess, myFileName, myName, myContext.get(mySignature), myContext.get(mySuperClass), myInterfaces,
        myFields, myMethods, myAnnotations, myTargets, myRetentionPolicy, myContext.get(myOuterClassName.get()), myLocalClassFlag.get(),
        myAnonymousClassFlag.get(), myUsages, myIsGenerated
      );
    }

    @Override
    public void visit(int version, int access, String name, String sig, String superName, String[] interfaces) {
      myTakeIntoAccount = notPrivate(access);

      myAccess = access;
      myName = myContext.get(name);
      myVersion = version;
      mySignature = sig;
      mySuperClass = superName;
      myInterfaces = interfaces;

      myClassNameHolder.set(name);

      if (mySuperClass != null) {
        final int superclassName = myContext.get(mySuperClass);
        myUsages.add(UsageRepr.createClassUsage(myContext, superclassName));
        //myUsages.add(UsageRepr.createClassExtendsUsage(myContext, superclassName));
      }

      if (myInterfaces != null) {
        for (String it : myInterfaces) {
          final int interfaceName = myContext.get(it);
          myUsages.add(UsageRepr.createClassUsage(myContext, interfaceName));
          //myUsages.add(UsageRepr.createClassExtendsUsage(myContext, interfaceName));
        }
      }

      processSignature(sig);
    }

    @Override
    public void visitEnd() {
      for (Map.Entry<TypeRepr.ClassType, Set<ElemType>> entry : myAnnotationTargets.entrySet()) {
        TypeRepr.ClassType type = entry.getKey();
        Set<ElemType> targets = entry.getValue();
        IntSet usedArguments = myAnnotationArguments.get(type);
        myUsages.add(UsageRepr.createAnnotationUsage(myContext, type, usedArguments, targets));
      }
    }

    @Override
    public ModuleVisitor visitModule(String name, int access, String version) {
      myIsModule = true;
      myAccess = access;
      myName = myContext.get(name);
      myVersion = myContext.get(version);
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

      final TypeRepr.ClassType annotationType = (TypeRepr.ClassType)TypeRepr.getType(myContext, desc);
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
          final TypeRepr.ClassType annotation = (TypeRepr.ClassType)TypeRepr.getType(myContext, desc);
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
              myFields.add(new FieldRepr(
                myContext, access, myContext.get(name), myContext.get(desc), myContext.get(signature), annotations, value
              ));
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
            myMethods.add(new MethodRepr(
              myContext, access, myContext.get(n), myContext.get(signature), desc, annotations, paramAnnotations, exceptions, defaultValue.get()
            ));
          }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
          final TypeRepr.ClassType annoType = (TypeRepr.ClassType)TypeRepr.getType(myContext, desc);
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
          final TypeRepr.ClassType annoType = (TypeRepr.ClassType)TypeRepr.getType(myContext, desc);
          paramAnnotations.add(new ParamAnnotation(parameter, annoType));
          return new AnnotationCrawler(annoType, ElemType.PARAMETER);
        }

        @Override
        public void visitLdcInsn(Object cst) {
          if (cst instanceof Type) {
            myUsages.add(UsageRepr.createClassUsage(myContext, myContext.get(((Type)cst).getInternalName())));
          }

          super.visitLdcInsn(cst);
        }

        @Override
        public void visitMultiANewArrayInsn(String desc, int dims) {
          final TypeRepr.ArrayType typ = (TypeRepr.ArrayType)TypeRepr.getType(myContext, desc);
          final TypeRepr.AbstractType element = typ.getDeepElementType();

          if (element instanceof TypeRepr.ClassType) {
            final int className = ((TypeRepr.ClassType)element).className;
            myUsages.add(UsageRepr.createClassUsage(myContext, className));
            myUsages.add(UsageRepr.createClassNewUsage(myContext, className));
          }

          typ.updateClassUsages(myContext, myName, myUsages);

          super.visitMultiANewArrayInsn(desc, dims);
        }

        @Override
        public void visitLocalVariable(String n, String desc, String signature, Label start, Label end, int index) {
          processSignature(signature);
          TypeRepr.getType(myContext, desc).updateClassUsages(myContext, myName, myUsages);
          super.visitLocalVariable(n, desc, signature, start, end, index);
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
          if (type != null) {
            TypeRepr.createClassType(myContext, myContext.get(type)).updateClassUsages(myContext, myName, myUsages);
          }

          super.visitTryCatchBlock(start, end, handler, type);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
          final TypeRepr.AbstractType typ = type.startsWith("[")? TypeRepr.getType(myContext, type) : TypeRepr.createClassType(myContext, myContext.get(type));

          if (opcode == Opcodes.NEW) {
            myUsages.add(UsageRepr.createClassUsage(myContext, ((TypeRepr.ClassType)typ).className));
            myUsages.add(UsageRepr.createClassNewUsage(myContext, ((TypeRepr.ClassType)typ).className));
            final int ktLambdaMarker = type.indexOf(KOTLIN_LAMBDA_USAGE_CLASS_MARKER);
            if (ktLambdaMarker > 0) {
              final int ifNameStart = ktLambdaMarker + KOTLIN_LAMBDA_USAGE_CLASS_MARKER.length();
              final int ifNameEnd = type.indexOf("$", ifNameStart);
              if (ifNameEnd > ifNameStart) {
                myUsages.add(UsageRepr.createClassNewUsage(myContext, myContext.get(type.substring(ifNameStart, ifNameEnd).replace('_', '/'))));
              }
            }
          }
          else if (opcode == Opcodes.ANEWARRAY) {
            if (typ instanceof TypeRepr.ClassType) {
              myUsages.add(UsageRepr.createClassUsage(myContext, ((TypeRepr.ClassType)typ).className));
              myUsages.add(UsageRepr.createClassNewUsage(myContext, ((TypeRepr.ClassType)typ).className));
            }
          }

          typ.updateClassUsages(myContext, myName, myUsages);

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
          addClassUsage(TypeRepr.getType(myContext, returnType));

          // common args processing
          for (Object arg : bsmArgs) {
            if (arg instanceof Type) {
              final Type type = (Type)arg;
              if (type.getSort() == Type.METHOD) {
                for (Type argType : type.getArgumentTypes()) {
                  addClassUsage(TypeRepr.getType(myContext, argType));
                }
                addClassUsage(TypeRepr.getType(myContext, type.getReturnType()));
              }
              else {
                addClassUsage(TypeRepr.getType(myContext, type));
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
                  myUsages.add(UsageRepr.createClassNewUsage(myContext, myContext.get(returnType.getInternalName())));
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
          final int fieldName = myContext.get(fName);
          final int fieldOwner = myContext.get(owner);
          final int descr = myContext.get(desc);
          if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
            myUsages.add(UsageRepr.createFieldAssignUsage(myContext, fieldName, fieldOwner, descr));
          }
          if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) {
            addClassUsage(TypeRepr.getType(myContext, descr));
          }
          myUsages.add(UsageRepr.createFieldUsage(myContext, fieldName, fieldOwner, descr));
        }

        private void registerMethodUsage(String owner, String name, @Nullable String desc) {
          final int methodOwner = myContext.get(owner);
          final int methodName = myContext.get(name);
          myUsages.add(UsageRepr.createMetaMethodUsage(myContext, methodName, methodOwner));
          if (desc != null) {
            myUsages.add(UsageRepr.createMethodUsage(myContext, methodName, methodOwner, desc));
            addClassUsage(TypeRepr.getType(myContext, Type.getReturnType(desc)));
          }
        }

        private void addClassUsage(final TypeRepr.AbstractType type) {
          TypeRepr.ClassType classType = null;
          if (type instanceof TypeRepr.ClassType) {
            classType = (TypeRepr.ClassType)type;
          }
          else if (type instanceof TypeRepr.ArrayType) {
            final TypeRepr.AbstractType elemType = ((TypeRepr.ArrayType)type).getDeepElementType();
            if (elemType instanceof TypeRepr.ClassType) {
              classType = (TypeRepr.ClassType)elemType;
            }
          }
          if (classType != null) {
            myUsages.add(UsageRepr.createClassUsage(myContext, classType.className));
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
      public void visitBaseType(char descriptor) { }

      @Override
      public void visitTypeVariable(String name) { }

      @Override
      public SignatureVisitor visitArrayType() {
        return super.visitArrayType();
      }

      @Override
      public void visitInnerClassType(String name) { }

      @Override
      public void visitTypeArgument() { super.visitTypeArgument(); }

      @Override
      public SignatureVisitor visitTypeArgument(char wildcard) {
        return this;
      }

      @Override
      public void visitEnd() { super.visitEnd(); }

      @Override
      public void visitClassType(String name) {
        int className = myContext.get(name);
        myUsages.add(UsageRepr.createClassUsage(myContext, className));
      }
    }
  }

  public ClassFileRepr analyze(int fileName, ClassReader cr, boolean isGenerated) {
    ClassCrawler visitor = new ClassCrawler(fileName, isGenerated);

    try {
      cr.accept(visitor, 0);
    }
    catch (RuntimeException e) {
      throw new RuntimeException("Corrupted .class file: " + myContext.getValue(fileName), e);
    }

    return visitor.getResult();
  }
}