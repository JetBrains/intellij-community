package org.jetbrains.ether.dependencyView;

import groovyjarjarasm.asm.Opcodes;
import org.jetbrains.ether.RW;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 01.02.11
 * Time: 4:54
 * To change this template use File | Settings | File Templates.
 */
public class ClassRepr extends Proto {
  public final StringCache.S sourceFileName;
  public final StringCache.S fileName;
  public final TypeRepr.AbstractType superClass;
  public final Set<TypeRepr.AbstractType> interfaces;
  public final Set<TypeRepr.AbstractType> nestedClasses;
  public final Set<ElementType> targets;
  public final RetentionPolicy policy;

  public final Set<FieldRepr> fields;
  public final Set<MethodRepr> methods;

  public final StringCache.S outerClassName;
  public final boolean isLocal;

  public String getFileName () {
    return fileName.value;
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
        final String pastSuperName = ((TypeRepr.ClassType)((ClassRepr)past).superClass).className.value;
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

  public StringCache.S[] getSupers() {
    final StringCache.S[] result = new StringCache.S[interfaces.size() + 1];

    result[0] = ((TypeRepr.ClassType)superClass).className;

    int i = 1;
    for (TypeRepr.AbstractType t : interfaces) {
      result[i++] = ((TypeRepr.ClassType)t).className;
    }

    return result;
  }

  public void updateClassUsages(final UsageRepr.Cluster s) {
    superClass.updateClassUsages(name, s);

    for (TypeRepr.AbstractType t : interfaces) {
      t.updateClassUsages(name, s);
    }

    for (MethodRepr m : methods) {
      m.updateClassUsages(name, s);
    }

    for (FieldRepr f : fields) {
      f.updateClassUsages(name, s);
    }
  }

  public ClassRepr(final int a,
                   final StringCache.S sn,
                   final StringCache.S fn,
                   final StringCache.S n,
                   final String sig,
                   final String sup,
                   final String[] i,
                   final Collection<String> ns,
                   final Set<FieldRepr> f,
                   final Set<MethodRepr> m,
                   final Set<ElementType> targets,
                   final RetentionPolicy policy,
                   final String outerClassName,
                   final boolean localClassFlag) {
    super(a, sig, n);
    fileName = fn;
    sourceFileName = sn;
    superClass = TypeRepr.createClassType(sup);
    interfaces = (Set<TypeRepr.AbstractType>)TypeRepr.createClassType(i, new HashSet<TypeRepr.AbstractType>());
    nestedClasses = (Set<TypeRepr.AbstractType>)TypeRepr.createClassType(ns, new HashSet<TypeRepr.AbstractType>());
    fields = f;
    methods = m;
    this.targets = targets;
    this.policy = policy;
    this.outerClassName = StringCache.get(outerClassName);
    this.isLocal = localClassFlag;
  }

  public ClassRepr(final BufferedReader r) {
    super(r);
    fileName = StringCache.get(RW.readString(r));
    sourceFileName = StringCache.get(RW.readString(r));
    superClass = TypeRepr.reader.read(r);
    interfaces = (Set<TypeRepr.AbstractType>)RW.readMany(r, TypeRepr.reader, new HashSet<TypeRepr.AbstractType>());
    nestedClasses = (Set<TypeRepr.AbstractType>)RW.readMany(r, TypeRepr.reader, new HashSet<TypeRepr.AbstractType>());
    fields = (Set<FieldRepr>)RW.readMany(r, FieldRepr.reader, new HashSet<FieldRepr>());
    methods = (Set<MethodRepr>)RW.readMany(r, MethodRepr.reader, new HashSet<MethodRepr>());
    targets = (Set<ElementType>)RW.readMany(r, UsageRepr.AnnotationUsage.elementTypeReader, new HashSet<ElementType>());

    final String s = RW.readString(r);

    policy = s.length() == 0 ? null : RetentionPolicy.valueOf(s);

    outerClassName = StringCache.get(RW.readString(r));
    isLocal = RW.readString(r).equals("true");
  }

  public boolean isAnnotation() {
    return (access & Opcodes.ACC_ANNOTATION) > 0;
  }

  public static RW.Reader<ClassRepr> reader = new RW.Reader<ClassRepr>() {
    public ClassRepr read(final BufferedReader r) {
      return new ClassRepr(r);
    }
  };

  public void write(final BufferedWriter w) {
    super.write(w);
    RW.writeln(w, fileName.value);
    RW.writeln(w, sourceFileName.value);
    superClass.write(w);
    RW.writeln(w, interfaces);
    RW.writeln(w, nestedClasses);
    RW.writeln(w, fields);
    RW.writeln(w, methods);
    RW.writeln(w, targets, UsageRepr.AnnotationUsage.elementTypeToWritable);
    RW.writeln(w, policy == null ? "" : policy.toString());
    RW.writeln(w, outerClassName.value);
    RW.writeln(w, isLocal ? "true" : "false");
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
    return UsageRepr.createClassUsage(name);
  }

  public StringCache.S getSourceFileName() {
    return sourceFileName;
  }

  public String getPackageName() {
    return getPackageName(name);
  }

  public static String getPackageName(final StringCache.S s) {
    return getPackageName(s.value);

  }

  public static String getPackageName(final String raw) {
    final int index = raw.lastIndexOf('/');

    if (index == -1) {
      return "";
    }

    return raw.substring(0, index);
  }

  public FieldRepr findField(final StringCache.S name) {
    for (FieldRepr f : fields) {
      if (f.name.equals(name)) {
        return f;
      }
    }

    return null;
  }

  public MethodRepr findMethod(final MethodRepr m) {
    for (MethodRepr mm : methods) {
      if (mm.equals(m)) {
        return mm;
      }
    }

    return null;
  }
}
