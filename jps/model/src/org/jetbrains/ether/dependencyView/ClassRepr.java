package org.jetbrains.ether.dependencyView;

import com.intellij.util.io.DataExternalizer;
import groovyjarjarasm.asm.Opcodes;
import org.jetbrains.ether.RW;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 01.02.11
 * Time: 4:54
 * To change this template use File | Settings | File Templates.
 */
public class ClassRepr extends Proto {
  private final DependencyContext context;
  public final DependencyContext.S sourceFileName;
  public final DependencyContext.S fileName;
  public final TypeRepr.AbstractType superClass;
  public final Set<TypeRepr.AbstractType> interfaces;
  public final Set<TypeRepr.AbstractType> nestedClasses;
  public final Set<ElementType> targets;
  public final RetentionPolicy policy;

  public final Set<FieldRepr> fields;
  public final Set<MethodRepr> methods;

  public final DependencyContext.S outerClassName;
  public final boolean isLocal;

  public String getFileName() {
    return context.getValue(fileName);
  }

  public abstract class Diff extends Difference {
    public abstract Specifier<TypeRepr.AbstractType> interfaces();

    public abstract Specifier<TypeRepr.AbstractType> nestedClasses();

    public abstract Specifier<FieldRepr> fields();

    public abstract Specifier<MethodRepr> methods();

    public abstract Specifier<ElementType> targets();

    public abstract boolean retentionChanged();

    public abstract boolean extendsAdded();

    public boolean no() {
      return base() == NONE &&
             interfaces().unchanged() &&
             nestedClasses().unchanged() &&
             fields().unchanged() &&
             methods().unchanged() &&
             targets().unchanged() &&
             !retentionChanged();
    }
  }

  public Diff difference(final Proto past) {
    final ClassRepr pastClass = (ClassRepr)past;
    final Difference diff = super.difference(past);
    int base = diff.base();

    if (!superClass.equals(pastClass.superClass)) {
      base |= Difference.SUPERCLASS;
    }

    final int d = base;

    return new Diff() {
      @Override
      public boolean extendsAdded() {
        final String pastSuperName = context.getValue(((TypeRepr.ClassType)((ClassRepr)past).superClass).className);
        return (d & Difference.SUPERCLASS) > 0 && pastSuperName.equals("java/lang/Object");
      }

      @Override
      public boolean packageLocalOn() {
        return diff.packageLocalOn();
      }

      @Override
      public int addedModifiers() {
        return diff.addedModifiers();
      }

      @Override
      public int removedModifiers() {
        return diff.removedModifiers();
      }

      @Override
      public Difference.Specifier<TypeRepr.AbstractType> interfaces() {
        return Difference.make(pastClass.interfaces, interfaces);
      }

      @Override
      public Difference.Specifier<TypeRepr.AbstractType> nestedClasses() {
        return Difference.make(pastClass.nestedClasses, nestedClasses);
      }

      @Override
      public Difference.Specifier<FieldRepr> fields() {
        return Difference.make(pastClass.fields, fields);
      }

      @Override
      public Difference.Specifier<MethodRepr> methods() {
        return Difference.make(pastClass.methods, methods);
      }

      @Override
      public Specifier<ElementType> targets() {
        return Difference.make(pastClass.targets, targets);
      }

      @Override
      public boolean retentionChanged() {
        return !((policy == null && pastClass.policy == RetentionPolicy.CLASS) ||
                 (policy == RetentionPolicy.CLASS && pastClass.policy == null) ||
                 (policy == pastClass.policy));
      }

      @Override
      public int base() {
        return d;
      }
    };
  }

  public DependencyContext.S[] getSupers() {
    final DependencyContext.S[] result = new DependencyContext.S[interfaces.size() + 1];

    result[0] = ((TypeRepr.ClassType)superClass).className;

    int i = 1;
    for (TypeRepr.AbstractType t : interfaces) {
      result[i++] = ((TypeRepr.ClassType)t).className;
    }

    return result;
  }

  public void updateClassUsages(final DependencyContext context, final UsageRepr.Cluster s) {
    superClass.updateClassUsages(context, name, s);

    for (TypeRepr.AbstractType t : interfaces) {
      t.updateClassUsages(context, name, s);
    }

    for (MethodRepr m : methods) {
      m.updateClassUsages(context, name, s);
    }

    for (FieldRepr f : fields) {
      f.updateClassUsages(context, name, s);
    }
  }

  public ClassRepr(final DependencyContext context,
                   final int a,
                   final DependencyContext.S sn,
                   final DependencyContext.S fn,
                   final DependencyContext.S n,
                   final DependencyContext.S sig,
                   final DependencyContext.S sup,
                   final String[] i,
                   final Collection<String> ns,
                   final Set<FieldRepr> f,
                   final Set<MethodRepr> m,
                   final Set<ElementType> targets,
                   final RetentionPolicy policy,
                   final DependencyContext.S outerClassName,
                   final boolean localClassFlag) {
    super(a, sig, n);
    this.context = context;
    fileName = fn;
    sourceFileName = sn;
    superClass = TypeRepr.createClassType(context, sup);
    interfaces = (Set<TypeRepr.AbstractType>)TypeRepr.createClassType(context, i, new HashSet<TypeRepr.AbstractType>());
    nestedClasses = (Set<TypeRepr.AbstractType>)TypeRepr.createClassType(context, ns, new HashSet<TypeRepr.AbstractType>());
    fields = f;
    methods = m;
    this.targets = targets;
    this.policy = policy;
    this.outerClassName = outerClassName;
    this.isLocal = localClassFlag;
  }

  public ClassRepr(final DependencyContext context, final DataInput in) {
    super(in);
    try {
      this.context = context;
      fileName = new DependencyContext.S(in);
      sourceFileName = new DependencyContext.S(in);
      superClass = TypeRepr.externalizer(context).read(in);
      interfaces = (Set<TypeRepr.AbstractType>)RW.read(TypeRepr.externalizer(context), new HashSet<TypeRepr.AbstractType>(), in);
      nestedClasses = (Set<TypeRepr.AbstractType>)RW.read(TypeRepr.externalizer(context), new HashSet<TypeRepr.AbstractType>(), in);
      fields = (Set<FieldRepr>)RW.read(FieldRepr.externalizer(context), new HashSet<FieldRepr>(), in);
      methods = (Set<MethodRepr>)RW.read(MethodRepr.externalizer(context), new HashSet<MethodRepr>(), in);
      targets = (Set<ElementType>)RW.read(UsageRepr.AnnotationUsage.elementTypeExternalizer, new HashSet<ElementType>(), in);

      final String s = in.readUTF();

      policy = s.length() == 0 ? null : RetentionPolicy.valueOf(s);

      outerClassName = new DependencyContext.S(in);
      isLocal = in.readBoolean();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void save(final DataOutput out) {
    try {
      super.save(out);
      fileName.save(out);
      sourceFileName.save(out);
      superClass.save(out);
      RW.save(interfaces, out);
      RW.save(nestedClasses, out);
      RW.save(fields, out);
      RW.save(methods, out);
      RW.save(targets, UsageRepr.AnnotationUsage.elementTypeExternalizer, out);
      out.writeUTF(policy == null ? "" : policy.toString());
      outerClassName.save(out);
      out.writeBoolean(isLocal);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean isAnnotation() {
    return (access & Opcodes.ACC_ANNOTATION) > 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ClassRepr classRepr = (ClassRepr)o;

    if (fileName != null ? !fileName.equals(classRepr.fileName) : classRepr.fileName != null) return false;
    if (name != null ? !name.equals(classRepr.name) : classRepr.name != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = fileName != null ? fileName.hashCode() : 0;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    return result;
  }

  public UsageRepr.Usage createUsage() {
    return UsageRepr.createClassUsage(context, name);
  }

  public DependencyContext.S getSourceFileName() {
    return sourceFileName;
  }

  public String getPackageName() {
    return getPackageName(name);
  }

  public String getPackageName(final DependencyContext.S s) {
    return getPackageName(context.getValue(s));

  }

  public static String getPackageName(final String raw) {
    final int index = raw.lastIndexOf('/');

    if (index == -1) {
      return "";
    }

    return raw.substring(0, index);
  }

  public FieldRepr findField(final DependencyContext.S name) {
    for (FieldRepr f : fields) {
      if (f.name.equals(name)) {
        return f;
      }
    }

    return null;
  }

  public Collection<MethodRepr> findMethodsByJavaRules(final MethodRepr m) {
    final List<MethodRepr> result = new LinkedList<MethodRepr>();

    for (MethodRepr mm : methods) {
      if (mm.equalsByJavaRules(m)) {
        result.add(mm);
      }
    }

    return result;
  }

  public MethodRepr findMethod(final MethodRepr m) {
    for (MethodRepr mm : methods) {
      if (mm.equals(m)) {
        return mm;
      }
    }

    return null;
  }

  public final static DataExternalizer<ClassRepr> externalizer(final DependencyContext context) {
    return new DataExternalizer<ClassRepr>() {
      @Override
      public void save(final DataOutput out, final ClassRepr value) throws IOException {
        value.save(out);
      }

      @Override
      public ClassRepr read(final DataInput in) throws IOException {
        return new ClassRepr(context, in);
      }
    };
  }
}
