package org.jetbrains.ether.dependencyView;

import com.intellij.openapi.util.Pair;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.EmptyVisitor;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.lang.annotation.ElementType;
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

  //private static String denullify (final String s) {
  //  return s == null ? "" : s;
  //}

  private class ClassCrawler extends EmptyVisitor {
    private class AnnotationRetentionPolicyCrawler implements AnnotationVisitor {
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

    private class AnnotationTargetCrawler implements AnnotationVisitor {
      public void visit(String name, Object value) {
      }

      public void visitEnum(final String name, String desc, final String value) {
        targets.add(ElementType.valueOf(value));
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

    private class AnnotationCrawler implements AnnotationVisitor {
      private final TypeRepr.ClassType type;
      private final ElementType target;

      private final Set<DependencyContext.S> usedArguments = new HashSet<DependencyContext.S>();

      private AnnotationCrawler(final TypeRepr.ClassType type, final ElementType target) {
        this.type = type;
        this.target = target;
        annotationTargets.put(type, target);
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
        usages.addUsage(context.get(classNameHolder.get()), UsageRepr.createMethodUsage(context, context.get(name), type.className, getMethodDescr(
          value)));
        usedArguments.add(context.get(name));
      }

      public void visitEnum(String name, String desc, String value) {
        usages.addUsage(context.get(classNameHolder.get()), UsageRepr.createMethodUsage(context, context.get(name), type.className, "()" + desc));
        usedArguments.add(context.get(name));
      }

      public AnnotationVisitor visitAnnotation(String name, String desc) {
        return new AnnotationCrawler((TypeRepr.ClassType)TypeRepr.getType(context, context.get(desc)), target);
      }

      public AnnotationVisitor visitArray(String name) {
        usedArguments.add(context.get(name));
        return this;
      }

      public void visitEnd() {
        final Set<DependencyContext.S> s = annotationArguments.get(type);

        if (s == null) {
          annotationArguments.put(type, usedArguments);
        }
        else {
          s.retainAll(usedArguments);
        }
      }
    }

    private void processSignature(final String sig) {
      if (sig != null) new SignatureReader(sig).accept(signatureCrawler);
    }

    private final SignatureVisitor signatureCrawler = new SignatureVisitor() {
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

    final DependencyContext.S fileName;
    int access;
    DependencyContext.S name;
    String superClass;
    String[] interfaces;
    String signature;
    DependencyContext.S sourceFile;

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
    final Set<ElementType> targets = new HashSet<ElementType>();
    RetentionPolicy policy = null;

    private TransientMultiMaplet.CollectionConstructor<ElementType> elementTypeSetConstructor = new TransientMultiMaplet.CollectionConstructor<ElementType>() {
      public Set<ElementType> create() {
        return new HashSet<ElementType>();
      }
    };

    final Map<TypeRepr.ClassType, Set<DependencyContext.S>> annotationArguments = new HashMap<TypeRepr.ClassType, Set<DependencyContext.S>>();
    final TransientMultiMaplet<TypeRepr.ClassType, ElementType> annotationTargets =
      new TransientMultiMaplet<TypeRepr.ClassType, ElementType>(elementTypeSetConstructor);

    public ClassCrawler(final DependencyContext.S fn) {
      fileName = fn;
    }

    private boolean notPrivate(final int access) {
      return (access & Opcodes.ACC_PRIVATE) == 0;
    }

    public Pair<ClassRepr, Pair<UsageRepr.Cluster, Set<UsageRepr.Usage>>> getResult() {
      final ClassRepr repr =
        takeIntoAccount ? new ClassRepr(context, access, sourceFile, fileName, name, context.get(signature), context.get(superClass), interfaces, nestedClasses, fields,
                                        methods, targets, policy, context.get(outerClassName.get()), localClassFlag.get()) : null;

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

      if (superClass != null) {
        usages.addUsage(context.get(classNameHolder.get()), UsageRepr.createClassUsage(context, context.get(superClass)));
        usages.addUsage(context.get(classNameHolder.get()), UsageRepr.createClassExtendsUsage(context, context.get(superClass)));
      }

      if (interfaces != null) {
        for (String it : interfaces) {
          usages.addUsage(context.get(classNameHolder.get()), UsageRepr.createClassUsage(context, context.get(it)));
          usages.addUsage(context.get(classNameHolder.get()), UsageRepr.createClassExtendsUsage(context, context.get(it)));
        }
      }

      processSignature(sig);
    }

    @Override
    public void visitEnd() {
      for (TypeRepr.ClassType type : annotationTargets.keyCollection()) {
        final Collection<ElementType> targets = annotationTargets.get(type);
        final Set<DependencyContext.S> usedArguments = annotationArguments.get(type);

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

      return new AnnotationCrawler((TypeRepr.ClassType)TypeRepr.getType(context, context.get(desc)),
                                   (access & Opcodes.ACC_ANNOTATION) > 0 ? ElementType.ANNOTATION_TYPE : ElementType.TYPE);
    }

    @Override
    public void visitSource(String source, String debug) {
      sourceFile = context.get(source);
    }

    @Override
    public FieldVisitor visitField(int access, String n, String desc, String signature, Object value) {
      processSignature(signature);

      fields.add(new FieldRepr(context, access, context.get(n), context.get(desc), context.get(signature), value));

      return new EmptyVisitor() {
        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
          return new AnnotationCrawler((TypeRepr.ClassType)TypeRepr.getType(context, context.get(desc)), ElementType.FIELD);
        }
      };
    }

    @Override
    public MethodVisitor visitMethod(final int access,
                                     final String n,
                                     final String desc,
                                     final String signature,
                                     final String[] exceptions) {
      final Holder<Object> defaultValue = new Holder<Object>();

      processSignature(signature);

      return new EmptyVisitor() {
        @Override
        public void visitEnd() {
          methods.add(new MethodRepr(context, access, context.get(n), context.get(signature), desc, exceptions, defaultValue.get()));
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
          return new AnnotationCrawler((TypeRepr.ClassType)TypeRepr.getType(context, context.get(desc)),
                                       n.equals("<init>") ? ElementType.CONSTRUCTOR : ElementType.METHOD);
        }

        @Override
        public AnnotationVisitor visitAnnotationDefault() {
          return new EmptyVisitor() {
            public void visit(String name, Object value) {
              defaultValue.set(value);
            }
          };
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
          return new AnnotationCrawler((TypeRepr.ClassType)TypeRepr.getType(context, context.get(desc)), ElementType.PARAMETER);
        }

        @Override
        public void visitMultiANewArrayInsn(String desc, int dims) {
          final TypeRepr.ArrayType typ = (TypeRepr.ArrayType)TypeRepr.getType(context, context.get(desc));
          final TypeRepr.AbstractType element = typ.getDeepElementType();

          if (element instanceof TypeRepr.ClassType) {
            usages.addUsage(context.get(classNameHolder.get()), UsageRepr.createClassUsage(context, ((TypeRepr.ClassType)element).className));
            usages.addUsage(context.get(classNameHolder.get()), UsageRepr.createClassNewUsage(context, ((TypeRepr.ClassType)element).className));
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
            usages.addUsage(context.get(classNameHolder.get()), UsageRepr.createClassUsage(context, ((TypeRepr.ClassType)typ).className));
            usages.addUsage(context.get(classNameHolder.get()), UsageRepr.createClassNewUsage(context, ((TypeRepr.ClassType)typ).className));
          }
          else if (opcode == Opcodes.ANEWARRAY) {
            if (typ instanceof TypeRepr.ClassType) {
              usages.addUsage(context.get(classNameHolder.get()), UsageRepr.createClassUsage(context, ((TypeRepr.ClassType)typ).className));
              usages.addUsage(context.get(classNameHolder.get()), UsageRepr.createClassNewUsage(context, ((TypeRepr.ClassType)typ).className));
            }
          }

          typ.updateClassUsages(context, name, usages);

          super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
          if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
            usages.addUsage(context.get(classNameHolder.get()), UsageRepr.createFieldAssignUsage(context, context.get(name), context.get(owner), context.get(desc)));
          }
          usages.addUsage(context.get(classNameHolder.get()), UsageRepr.createFieldUsage(context, context.get(name), context.get(owner), context.get(desc)));
          super.visitFieldInsn(opcode, owner, name, desc);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
          usages.addUsage(context.get(classNameHolder.get()), UsageRepr.createMethodUsage(context, context.get(name), context.get(owner),
                                                                                          desc));
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

  public Pair<ClassRepr, Pair<UsageRepr.Cluster, Set<UsageRepr.Usage>>> analyze(final DependencyContext.S fileName, final ClassReader cr) {
    final ClassCrawler visitor = new ClassCrawler(fileName);

    cr.accept(visitor, 0);

    return visitor.getResult();
  }
}