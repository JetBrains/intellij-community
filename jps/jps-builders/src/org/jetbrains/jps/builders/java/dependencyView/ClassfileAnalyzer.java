/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.openapi.util.Pair;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TIntHashSet;
import org.jetbrains.asm4.*;
import org.jetbrains.asm4.signature.SignatureReader;
import org.jetbrains.asm4.signature.SignatureVisitor;

import java.lang.annotation.RetentionPolicy;
import java.util.*;

/**
 * @author: db
 * Date: 31.01.11
 */

class ClassfileAnalyzer {
  private final DependencyContext myContext;

  ClassfileAnalyzer(DependencyContext context) {
    this.myContext = context;
  }

  private static class Holder<T> {
    private T x = null;

    public void set(final T x) {
      this.x = x;
    }

    public T get() {
      return x;
    }
  }

  private class ClassCrawler extends ClassVisitor {
    private class AnnotationRetentionPolicyCrawler extends AnnotationVisitor {
      private AnnotationRetentionPolicyCrawler() {
        super(Opcodes.ASM4);
      }

      public void visit(String name, Object value) {
      }

      public void visitEnum(String name, String desc, String value) {
        myRetentionPolicy = RetentionPolicy.valueOf(value);
      }

      public AnnotationVisitor visitAnnotation(String name, String desc) {
        return null;
      }

      public AnnotationVisitor visitArray(String name) {
        return null;
      }

      public void visitEnd() {
      }
    }

    private class AnnotationTargetCrawler extends AnnotationVisitor {
      private AnnotationTargetCrawler() {
        super(Opcodes.ASM4);
      }

      public void visit(String name, Object value) {
      }

      public void visitEnum(final String name, String desc, final String value) {
        myTargets.add(ElemType.valueOf(value));
      }

      public AnnotationVisitor visitAnnotation(String name, String desc) {
        return this;
      }

      public AnnotationVisitor visitArray(String name) {
        return this;
      }

      public void visitEnd() {
      }
    }

    private class AnnotationCrawler extends AnnotationVisitor {
      private final TypeRepr.ClassType myType;
      private final ElemType myTarget;

      private final TIntHashSet myUsedArguments = new TIntHashSet();

      private AnnotationCrawler(final TypeRepr.ClassType type, final ElemType target) {
        super(Opcodes.ASM4);
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

      private String getMethodDescr(final Object value) {
        if (value instanceof Type) {
          return "()Ljava/lang/Class;";
        }

        final String name = Type.getType(value.getClass()).getInternalName();

        if (name.equals("java/lang/Integer")) {
          return "()I;";
        }

        if (name.equals("java/lang/Short")) {
          return "()S;";
        }

        if (name.equals("java/lang/Long")) {
          return "()J;";
        }

        if (name.equals("java/lang/Byte")) {
          return "()B;";
        }

        if (name.equals("java/lang/Char")) {
          return "()C;";
        }

        if (name.equals("java/lang/Boolean")) {
          return "()Z;";
        }

        if (name.equals("java/lang/Float")) {
          return "()F;";
        }

        if (name.equals("java/lang/Double")) {
          return "()D;";
        }

        final String s = "()L" + name + ";";

        return s;
      }

      public void visit(String name, Object value) {
        final String methodDescr = getMethodDescr(value);
        final int methodName = myContext.get(name);

        if (value instanceof Type) {
          final String className = ((Type)value).getClassName().replace('.', '/');

          if (className != null) {
            myUsages.add(UsageRepr.createClassUsage(myContext, myContext.get(className)));
          }
        }

        myUsages.add(UsageRepr.createMethodUsage(myContext, methodName, myType.className, methodDescr));
        myUsages.add(UsageRepr.createMetaMethodUsage(myContext, methodName, myType.className, methodDescr));

        myUsedArguments.add(methodName);
      }

      public void visitEnum(String name, String desc, String value) {
        final int methodName = myContext.get(name);
        final String methodDescr = "()" + desc;

        myUsages.add(UsageRepr.createMethodUsage(myContext, methodName, myType.className, methodDescr));
        myUsages.add(UsageRepr.createMetaMethodUsage(myContext, methodName, myType.className, methodDescr));

        myUsedArguments.add(methodName);
      }

      public AnnotationVisitor visitAnnotation(String name, String desc) {
        return new AnnotationCrawler((TypeRepr.ClassType)TypeRepr.getType(myContext, myContext.get(desc)), myTarget);
      }

      public AnnotationVisitor visitArray(String name) {
        myUsedArguments.add(myContext.get(name));
        return this;
      }

      public void visitEnd() {
        final TIntHashSet s = myAnnotationArguments.get(myType);

        if (s == null) {
          myAnnotationArguments.put(myType, myUsedArguments);
        }
        else {
          s.retainAll(myUsedArguments.toArray());
        }
      }
    }

    private void processSignature(final String sig) {
      if (sig != null) {
        new SignatureReader(sig).accept(mySignatureCrawler);
      }
    }

    private final SignatureVisitor mySignatureCrawler = new SignatureVisitor(Opcodes.ASM4) {
      public void visitFormalTypeParameter(String name) {
        return;
      }

      public SignatureVisitor visitClassBound() {
        return this;
      }

      public SignatureVisitor visitInterfaceBound() {
        return this;
      }

      public SignatureVisitor visitSuperclass() {
        return this;
      }

      public SignatureVisitor visitInterface() {
        return this;
      }

      public SignatureVisitor visitParameterType() {
        return this;
      }

      public SignatureVisitor visitReturnType() {
        return this;
      }

      public SignatureVisitor visitExceptionType() {
        return this;
      }

      public void visitBaseType(char descriptor) {
        return;
      }

      public void visitTypeVariable(String name) {
        return;
      }

      public SignatureVisitor visitArrayType() {
        return this;
      }

      public void visitInnerClassType(String name) {
        return;
      }

      public void visitTypeArgument() {
        return;
      }

      public SignatureVisitor visitTypeArgument(char wildcard) {
        return this;
      }

      public void visitEnd() {
      }

      public void visitClassType(String name) {
        final int className = myContext.get(name);
        myUsages.add(UsageRepr.createClassUsage(myContext, className));
        myUsages.add(UsageRepr.createClassAsGenericBoundUsage(myContext, className));
      }
    };

    private Boolean myTakeIntoAccount = false;

    private final int myFileName;
    private int myAccess;
    private int myName;
    private String mySuperClass;
    private String[] myInterfaces;
    private String mySignature;

    final Holder<String> myClassNameHolder = new Holder<String>();
    final Holder<String> myOuterClassName = new Holder<String>();
    final Holder<Boolean> myLocalClassFlag = new Holder<Boolean>();
    final Holder<Boolean> myAnonymousClassFlag = new Holder<Boolean>();

    {
      myLocalClassFlag.set(false);
      myAnonymousClassFlag.set(false);
    }

    private final Set<MethodRepr> myMethods = new THashSet<MethodRepr>();
    private final Set<FieldRepr> myFields = new THashSet<FieldRepr>();
    private final Set<UsageRepr.Usage> myUsages = new THashSet<UsageRepr.Usage>();
    private final Set<ElemType> myTargets = EnumSet.noneOf(ElemType.class);
    private RetentionPolicy myRetentionPolicy = null;

    final Map<TypeRepr.ClassType, TIntHashSet> myAnnotationArguments = new THashMap<TypeRepr.ClassType, TIntHashSet>();
    final Map<TypeRepr.ClassType, Set<ElemType>> myAnnotationTargets = new THashMap<TypeRepr.ClassType, Set<ElemType>>();

    public ClassCrawler(final int fn) {
      super(Opcodes.ASM4);
      myFileName = fn;
    }

    private boolean notPrivate(final int access) {
      return (access & Opcodes.ACC_PRIVATE) == 0;
    }

    public Pair<ClassRepr, Set<UsageRepr.Usage>> getResult() {
      final ClassRepr repr =
        myTakeIntoAccount ? new ClassRepr(
          myContext, myAccess, myFileName, myName, myContext.get(mySignature), myContext.get(mySuperClass), myInterfaces,
          myFields,
          myMethods, myTargets, myRetentionPolicy, myContext
          .get(myOuterClassName.get()), myLocalClassFlag.get(), myAnonymousClassFlag.get(), myUsages) : null;

      if (repr != null) {
        repr.updateClassUsages(myContext, myUsages);
      }

      return new Pair<ClassRepr, Set<UsageRepr.Usage>>(repr, myUsages);
    }

    @Override
    public void visit(int version, int a, String n, String sig, String s, String[] i) {
      myTakeIntoAccount = notPrivate(a);

      myAccess = a;
      myName = myContext.get(n);
      mySignature = sig;
      mySuperClass = s;
      myInterfaces = i;

      myClassNameHolder.set(n);

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
        final TypeRepr.ClassType type = entry.getKey();
        final Set<ElemType> targets = entry.getValue();
        final TIntHashSet usedArguments = myAnnotationArguments.get(type);

        myUsages.add(UsageRepr.createAnnotationUsage(myContext, type, usedArguments, targets));
      }
    }

    @Override
    public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
      if (desc.equals("Ljava/lang/annotation/Target;")) {
        return new AnnotationTargetCrawler();
      }

      if (desc.equals("Ljava/lang/annotation/Retention;")) {
        return new AnnotationRetentionPolicyCrawler();
      }

      return new AnnotationCrawler(
        (TypeRepr.ClassType)TypeRepr.getType(myContext, myContext.get(desc)),
        (myAccess & Opcodes.ACC_ANNOTATION) > 0 ? ElemType.ANNOTATION_TYPE : ElemType.TYPE
      );
    }

    @Override
    public void visitSource(String source, String debug) {
    }

    @Override
    public FieldVisitor visitField(int access, String n, String desc, String signature, Object value) {
      processSignature(signature);

      if ((access & Opcodes.ACC_SYNTHETIC) == 0) {
        myFields.add(new FieldRepr(myContext, access, myContext.get(n), myContext.get(desc), myContext.get(signature), value));
      }

      return new FieldVisitor(Opcodes.ASM4) {
        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
          return new AnnotationCrawler((TypeRepr.ClassType)TypeRepr.getType(myContext, myContext.get(desc)), ElemType.FIELD);
        }
      };
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String n, final String desc, final String signature, final String[] exceptions) {
      final Holder<Object> defaultValue = new Holder<Object>();

      processSignature(signature);

      return new MethodVisitor(Opcodes.ASM4) {
        @Override
        public void visitEnd() {
          if ((access & Opcodes.ACC_SYNTHETIC) == 0 || (access & Opcodes.ACC_BRIDGE) > 0) {
            myMethods.add(new MethodRepr(myContext, access, myContext.get(n), myContext.get(signature), desc, exceptions, defaultValue.get()));
          }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
          return new AnnotationCrawler(
            (TypeRepr.ClassType)TypeRepr.getType(myContext, myContext.get(desc)), "<init>".equals(n) ? ElemType.CONSTRUCTOR : ElemType.METHOD
          );
        }

        @Override
        public AnnotationVisitor visitAnnotationDefault() {
          return new AnnotationVisitor(Opcodes.ASM4) {
            public void visit(String name, Object value) {
              defaultValue.set(value);
            }
          };
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
          return new AnnotationCrawler((TypeRepr.ClassType)TypeRepr.getType(myContext, myContext.get(desc)), ElemType.PARAMETER);
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
          final TypeRepr.ArrayType typ = (TypeRepr.ArrayType)TypeRepr.getType(myContext, myContext.get(desc));
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
          TypeRepr.getType(myContext, myContext.get(desc)).updateClassUsages(myContext, myName, myUsages);
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
          final TypeRepr.AbstractType typ = type.startsWith("[") ? TypeRepr.getType(myContext, myContext.get(type)) : TypeRepr.createClassType(
            myContext, myContext.get(type));

          if (opcode == Opcodes.NEW) {
            myUsages.add(UsageRepr.createClassUsage(myContext, ((TypeRepr.ClassType)typ).className));
            myUsages.add(UsageRepr.createClassNewUsage(myContext, ((TypeRepr.ClassType)typ).className));
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
          final int fieldName = myContext.get(name);
          final int fieldOwner = myContext.get(owner);
          final int descr = myContext.get(desc);

          if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
            myUsages.add(UsageRepr.createFieldAssignUsage(myContext, fieldName, fieldOwner, descr));
          }

          if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) {
            addClassUsage(TypeRepr.getType(myContext, descr));
          }

          myUsages.add(UsageRepr.createFieldUsage(myContext, fieldName, fieldOwner, descr));
          super.visitFieldInsn(opcode, owner, name, desc);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
          final int methodName = myContext.get(name);
          final int methodOwner = myContext.get(owner);

          myUsages.add(UsageRepr.createMethodUsage(myContext, methodName, methodOwner, desc));
          myUsages.add(UsageRepr.createMetaMethodUsage(myContext, methodName, methodOwner, desc));
          addClassUsage(TypeRepr.getType(myContext, Type.getReturnType(desc)));

          super.visitMethodInsn(opcode, owner, name, desc);
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

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
      if (outerName != null) {
        myOuterClassName.set(outerName);
      }
      if (innerName == null) {
        myAnonymousClassFlag.set(true);
      }
    }

    @Override
    public void visitOuterClass(final String owner, final String name, final String desc) {
      myOuterClassName.set(owner);

      if (name != null) {
        myLocalClassFlag.set(true);
      }
    }
  }

  public Pair<ClassRepr, Set<UsageRepr.Usage>> analyze(final int fileName, final ClassReader cr) {
    final ClassCrawler visitor = new ClassCrawler(fileName);

    cr.accept(visitor, 0);

    return visitor.getResult();
  }
}