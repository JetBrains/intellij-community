package org.jetbrains.ether.dependencyView;

import com.intellij.openapi.util.Pair;
import gnu.trove.TIntHashSet;
import org.jetbrains.asm4.*;
import org.jetbrains.asm4.signature.SignatureReader;
import org.jetbrains.asm4.signature.SignatureVisitor;

import java.lang.annotation.RetentionPolicy;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 31.01.11
 * Time: 2:00
 * To change this template use File | Settings | File Templates.
 */

class ClassfileAnalyzer {
  private final DependencyContext context;

  ClassfileAnalyzer(DependencyContext context) {
    this.context = context;
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
        policy = RetentionPolicy.valueOf(value);
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
        targets.add(ElemType.valueOf(value));
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
      private final TypeRepr.ClassType type;
      private final ElemType target;

      private final TIntHashSet myUsedArguments = new TIntHashSet();

      private AnnotationCrawler(final TypeRepr.ClassType type, final ElemType target) {
        super(Opcodes.ASM4);
        this.type = type;
        this.target = target;
        final Set<ElemType> targets = myAnnotationTargets.get(type);
        if (targets == null) {
          myAnnotationTargets.put(type, EnumSet.of(target));
        }
        else {
          targets.add(target);
        }
        usages.addUsage(context.get(classNameHolder.get()), UsageRepr.createClassUsage(context, type.className));
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
        final int residence = context.get(classNameHolder.get());
        final String methodDescr = getMethodDescr(value);
        final int methodName = context.get(name);

        usages.addUsage(residence, UsageRepr.createMethodUsage(context, methodName, type.className, methodDescr));
        usages.addUsage(residence, UsageRepr.createMetaMethodUsage(context, methodName, type.className, methodDescr));

        myUsedArguments.add(methodName);
      }

      public void visitEnum(String name, String desc, String value) {
        final int residence = context.get(classNameHolder.get());
        final int methodName = context.get(name);
        final String methodDescr = "()" + desc;

        usages.addUsage(residence, UsageRepr.createMethodUsage(context, methodName, type.className, methodDescr));
        usages.addUsage(residence, UsageRepr.createMetaMethodUsage(context, methodName, type.className, methodDescr));

        myUsedArguments.add(methodName);
      }

      public AnnotationVisitor visitAnnotation(String name, String desc) {
        return new AnnotationCrawler((TypeRepr.ClassType)TypeRepr.getType(context, context.get(desc)), target);
      }

      public AnnotationVisitor visitArray(String name) {
        myUsedArguments.add(context.get(name));
        return this;
      }

      public void visitEnd() {
        final TIntHashSet s = myAnnotationArguments.get(type);

        if (s == null) {
          myAnnotationArguments.put(type, myUsedArguments);
        }
        else {
          s.retainAll(myUsedArguments.toArray());
        }
      }
    }

    private void processSignature(final String sig) {
      if (sig != null) new SignatureReader(sig).accept(signatureCrawler);
    }

    private final SignatureVisitor signatureCrawler = new SignatureVisitor(Opcodes.ASM4) {
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
        usages.addUsage(context.get(classNameHolder.get()), UsageRepr.createClassUsage(context, context.get(name)));
      }
    };

    Boolean takeIntoAccount = false;

    final int fileName;
    int access;
    int name;
    String superClass;
    String[] interfaces;
    String signature;

    final Holder<String> classNameHolder = new Holder<String>();
    final Holder<String> outerClassName = new Holder<String>();
    final Holder<Boolean> localClassFlag = new Holder<Boolean>();

    {
      localClassFlag.set(false);
    }

    final Set<MethodRepr> methods = new HashSet<MethodRepr>();
    final Set<FieldRepr> fields = new HashSet<FieldRepr>();
    final List<String> nestedClasses = new ArrayList<String>();
    final UsageRepr.Cluster usages = new UsageRepr.Cluster();
    final Set<UsageRepr.Usage> annotationUsages = new HashSet<UsageRepr.Usage>();
    final Set<ElemType> targets = EnumSet.noneOf(ElemType.class);
    RetentionPolicy policy = null;

    final Map<TypeRepr.ClassType, TIntHashSet> myAnnotationArguments = new HashMap<TypeRepr.ClassType, TIntHashSet>();
    final Map<TypeRepr.ClassType, Set<ElemType>> myAnnotationTargets = new HashMap<TypeRepr.ClassType, Set<ElemType>>();

    public ClassCrawler(final int fn) {
      super(Opcodes.ASM4);
      fileName = fn;
    }

    private boolean notPrivate(final int access) {
      return (access & Opcodes.ACC_PRIVATE) == 0;
    }

    public Pair<ClassRepr, Pair<UsageRepr.Cluster, Set<UsageRepr.Usage>>> getResult() {
      final ClassRepr repr =
        takeIntoAccount ? new ClassRepr(
          context, access, fileName, name, context.get(signature), context.get(superClass), interfaces, nestedClasses, fields, methods, targets, policy, context.get(outerClassName.get()), localClassFlag.get()) : null;

      if (repr != null) {
        repr.updateClassUsages(context, usages);
      }

      return new Pair<ClassRepr, Pair<UsageRepr.Cluster, Set<UsageRepr.Usage>>>(repr,
                                                                                new Pair<UsageRepr.Cluster, Set<UsageRepr.Usage>>(usages,
                                                                                                                                  annotationUsages));
    }

    @Override
    public void visit(int version, int a, String n, String sig, String s, String[] i) {
      takeIntoAccount = notPrivate(a);

      access = a;
      name = context.get(n);
      signature = sig;
      superClass = s;
      interfaces = i;

      classNameHolder.set(n);

      final int residence = context.get(classNameHolder.get());

      if (superClass != null) {
        final int superclassName = context.get(superClass);
        usages.addUsage(residence, UsageRepr.createClassUsage(context, superclassName));
        usages.addUsage(residence, UsageRepr.createClassExtendsUsage(context, superclassName));
      }

      if (interfaces != null) {
        for (String it : interfaces) {
          final int interfaceName = context.get(it);
          usages.addUsage(residence, UsageRepr.createClassUsage(context, interfaceName));
          usages.addUsage(residence, UsageRepr.createClassExtendsUsage(context, interfaceName));
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

        annotationUsages.add(UsageRepr.createAnnotationUsage(context, type, usedArguments, targets));
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
        (TypeRepr.ClassType)TypeRepr.getType(context, context.get(desc)),
        (access & Opcodes.ACC_ANNOTATION) > 0 ? ElemType.ANNOTATION_TYPE : ElemType.TYPE
      );
    }

    @Override
    public void visitSource(String source, String debug) {
    }

    @Override
    public FieldVisitor visitField(int access, String n, String desc, String signature, Object value) {
      processSignature(signature);

      if ((access & Opcodes.ACC_SYNTHETIC) == 0) {
        fields.add(new FieldRepr(context, access, context.get(n), context.get(desc), context.get(signature), value));
      }

      return new FieldVisitor(Opcodes.ASM4) {
        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
          return new AnnotationCrawler((TypeRepr.ClassType)TypeRepr.getType(context, context.get(desc)), ElemType.FIELD);
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
            methods.add(new MethodRepr(context, access, context.get(n), context.get(signature), desc, exceptions, defaultValue.get()));
          }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
          return new AnnotationCrawler(
            (TypeRepr.ClassType)TypeRepr.getType(context, context.get(desc)), "<init>".equals(n) ? ElemType.CONSTRUCTOR : ElemType.METHOD
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
          return new AnnotationCrawler((TypeRepr.ClassType)TypeRepr.getType(context, context.get(desc)), ElemType.PARAMETER);
        }

        @Override
        public void visitLdcInsn(Object cst) {
          if (cst instanceof Type) {
            usages.addUsage(context.get(classNameHolder.get()), UsageRepr.createClassUsage(context, context.get(((Type)cst).getInternalName())));
          }

          super.visitLdcInsn(cst);
        }

        @Override
        public void visitMultiANewArrayInsn(String desc, int dims) {
          final TypeRepr.ArrayType typ = (TypeRepr.ArrayType)TypeRepr.getType(context, context.get(desc));
          final TypeRepr.AbstractType element = typ.getDeepElementType();

          if (element instanceof TypeRepr.ClassType) {
            final int residence = context.get(classNameHolder.get());
            final int className = ((TypeRepr.ClassType)element).className;
            usages.addUsage(residence, UsageRepr.createClassUsage(context, className));
            usages.addUsage(residence, UsageRepr.createClassNewUsage(context, className));
          }

          typ.updateClassUsages(context, name, usages);

          super.visitMultiANewArrayInsn(desc, dims);
        }

        @Override
        public void visitLocalVariable(String n, String desc, String signature, Label start, Label end, int index) {
          processSignature(signature);
          TypeRepr.getType(context, context.get(desc)).updateClassUsages(context, name, usages);
          super.visitLocalVariable(n, desc, signature, start, end, index);
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
          if (type != null) {
            TypeRepr.createClassType(context, context.get(type)).updateClassUsages(context, name, usages);
          }

          super.visitTryCatchBlock(start, end, handler, type);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
          final TypeRepr.AbstractType typ = type.startsWith("[") ? TypeRepr.getType(context, context.get(type)) : TypeRepr.createClassType(context, context.get(type));

          if (opcode == Opcodes.NEW) {
            final int residence = context.get(classNameHolder.get());
            usages.addUsage(residence, UsageRepr.createClassUsage(context, ((TypeRepr.ClassType)typ).className));
            usages.addUsage(residence, UsageRepr.createClassNewUsage(context, ((TypeRepr.ClassType)typ).className));
          }
          else if (opcode == Opcodes.ANEWARRAY) {
            if (typ instanceof TypeRepr.ClassType) {
              final int residence = context.get(classNameHolder.get());
              usages.addUsage(residence, UsageRepr.createClassUsage(context, ((TypeRepr.ClassType)typ).className));
              usages.addUsage(residence, UsageRepr.createClassNewUsage(context, ((TypeRepr.ClassType)typ).className));
            }
          }

          typ.updateClassUsages(context, name, usages);

          super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
          final int residence = context.get(classNameHolder.get());
          final int fieldName = context.get(name);
          final int fieldOwner = context.get(owner);
          final int descr = context.get(desc);

          if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
            usages.addUsage(residence, UsageRepr.createFieldAssignUsage(context, fieldName, fieldOwner, descr));
          }
          usages.addUsage(residence, UsageRepr.createFieldUsage(context, fieldName, fieldOwner, descr));
          super.visitFieldInsn(opcode, owner, name, desc);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
          final int residence = context.get(classNameHolder.get());
          final int methodName = context.get(name);
          final int methodOwner = context.get(owner);

          usages.addUsage(residence, UsageRepr.createMethodUsage(context, methodName, methodOwner, desc));
          usages.addUsage(residence, UsageRepr.createMetaMethodUsage(context, methodName, methodOwner, desc));

          super.visitMethodInsn(opcode, owner, name, desc);
        }
      };
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
      if (outerName != null && outerName.equals(name) && notPrivate(access)) {
        nestedClasses.add(innerName);
      }
    }

    @Override
    public void visitOuterClass(final String owner, final String name, final String desc) {
      outerClassName.set(owner);

      if (name != null) {
        localClassFlag.set(true);
      }
    }
  }

  public Pair<ClassRepr, Pair<UsageRepr.Cluster, Set<UsageRepr.Usage>>> analyze(final int fileName, final ClassReader cr) {
    final ClassCrawler visitor = new ClassCrawler(fileName);

    cr.accept(visitor, 0);

    return visitor.getResult();
  }
}