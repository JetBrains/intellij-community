package org.jetbrains.ether.dependencyView;

import com.intellij.util.io.DataExternalizer;
import groovyjarjarasm.asm.Opcodes;
import org.jetbrains.ether.RW;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintStream;
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
  private final int fileName;
  public final TypeRepr.AbstractType superClass;
  public final Set<TypeRepr.AbstractType> interfaces;
  public final Set<ElemType> targets;
  public final RetentionPolicy policy;

  public final Set<FieldRepr> fields;
  public final Set<MethodRepr> methods;

  public final int outerClassName;
  public final boolean isLocal;

  public abstract static class Diff extends Difference {
    public abstract Specifier<TypeRepr.AbstractType> interfaces();

    public abstract Specifier<FieldRepr> fields();

    public abstract Specifier<MethodRepr> methods();

    public abstract Specifier<ElemType> targets();

    public abstract boolean retentionChanged();

    public abstract boolean extendsAdded();

    public boolean no() {
      return base() == NONE &&
             interfaces().unchanged() &&
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
      public Difference.Specifier<FieldRepr> fields() {
        return Difference.make(pastClass.fields, fields);
      }

      @Override
      public Difference.Specifier<MethodRepr> methods() {
        return Difference.make(pastClass.methods, methods);
      }

      @Override
      public Specifier<ElemType> targets() {
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

      @Override
      public boolean hadValue() {
        return false;
      }

      @Override
      public boolean weakedAccess() {
        return diff.weakedAccess();
      }
    };
  }

  public int[] getSupers() {
    final int[] result = new int[interfaces.size() + 1];

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

  public ClassRepr(final DependencyContext context, final int a, final int fn, final int n, final int sig,
                   final int sup,
                   final String[] i,
                   final Collection<String> ns,
                   final Set<FieldRepr> f,
                   final Set<MethodRepr> m,
                   final Set<ElemType> targets,
                   final RetentionPolicy policy,
                   final int outerClassName,
                   final boolean localClassFlag) {
    super(a, sig, n);
    this.context = context;
    fileName = fn;
    superClass = TypeRepr.createClassType(context, sup);
    interfaces = (Set<TypeRepr.AbstractType>)TypeRepr.createClassType(context, i, new HashSet<TypeRepr.AbstractType>());
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
      fileName = in.readInt();
      superClass = TypeRepr.externalizer(context).read(in);
      interfaces = (Set<TypeRepr.AbstractType>)RW.read(TypeRepr.externalizer(context), new HashSet<TypeRepr.AbstractType>(), in);
      fields = (Set<FieldRepr>)RW.read(FieldRepr.externalizer(context), new HashSet<FieldRepr>(), in);
      methods = (Set<MethodRepr>)RW.read(MethodRepr.externalizer(context), new HashSet<MethodRepr>(), in);
      targets = (Set<ElemType>)RW.read(UsageRepr.AnnotationUsage.elementTypeExternalizer, EnumSet.noneOf(ElemType.class), in);

      final String s = in.readUTF();

      policy = s.length() == 0 ? null : RetentionPolicy.valueOf(s);

      outerClassName = in.readInt();
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
      out.writeInt(fileName);
      superClass.save(out);
      RW.save(interfaces, out);
      RW.save(fields, out);
      RW.save(methods, out);
      RW.save(targets, UsageRepr.AnnotationUsage.elementTypeExternalizer, out);
      out.writeUTF(policy == null ? "" : policy.toString());
      out.writeInt(outerClassName);
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

    if (fileName != classRepr.fileName) return false;
    if (name != classRepr.name) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return 31 * fileName + name;
  }

  public UsageRepr.Usage createUsage() {
    return UsageRepr.createClassUsage(context, name);
  }

  public String getPackageName() {
    return getPackageName(name);
  }

  public String getPackageName(final int s) {
    return getPackageName(context.getValue(s));
  }

  public static String getPackageName(final String raw) {
    final int index = raw.lastIndexOf('/');

    if (index == -1) {
      return "";
    }

    return raw.substring(0, index);
  }

  public FieldRepr findField(final int name) {
    for (FieldRepr f : fields) {
      if (f.name == name) {
        return f;
      }
    }

    return null;
  }

  public Collection<MethodRepr> findMethods(final MethodRepr.Predicate p) {
    final Collection<MethodRepr> result = new LinkedList<MethodRepr>();

    for (MethodRepr mm : methods) {
      if (p.satisfy(mm)) {
        result.add(mm);
      }
    }

    return result;
  }

  public static DataExternalizer<ClassRepr> externalizer(final DependencyContext context) {
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

  @Override
  public void toStream(final DependencyContext context, final PrintStream stream) {
    super.toStream(context, stream);

    stream.print("      Filename   : ");
    stream.println(context.getValue(fileName));

    stream.print("      Superclass : ");
    stream.println(superClass == null ? "<null>" : superClass.getDescr(context));

    stream.print("      Interfaces : ");
    final TypeRepr.AbstractType[] is = interfaces.toArray(new TypeRepr.AbstractType[interfaces.size()]);
    Arrays.sort(is, new Comparator<TypeRepr.AbstractType>() {
      @Override
      public int compare(final TypeRepr.AbstractType o1, final TypeRepr.AbstractType o2) {
        return o1.getDescr(context).compareTo(o2.getDescr(context));
      }
    });
    for (final TypeRepr.AbstractType t : is) {
      stream.print(t.getDescr(context));
      stream.print(" ");
    }
    stream.println();

    stream.print("      Targets    : ");
    final ElemType[] es = targets.toArray(new ElemType[targets.size()]);
    Arrays.sort(es);
    for (final ElemType e : es) {
      stream.print(e);
      stream.print("; ");
    }
    stream.println();

    stream.print("      Policy     : ");
    stream.println(policy);

    stream.print("      Outer class: ");
    stream.println(context.getValue(outerClassName));

    stream.print("      Local class: ");
    stream.println(isLocal);

    stream.println("      Fields:");
    final FieldRepr[] fs = fields.toArray(new FieldRepr[fields.size()]);
    Arrays.sort(fs, new Comparator<FieldRepr>() {
      @Override
      public int compare(final FieldRepr o1, final FieldRepr o2) {
        if (o1.name == o2.name) {
          return o1.type.getDescr(context).compareTo(o2.type.getDescr(context));
        }

        return o1.name - o2.name;
      }
    });
    for (final FieldRepr f : fs) {
      f.toStream(context, stream);
    }
    stream.println("      End Of Fields");

    stream.println("      Methods:");
    final MethodRepr[] ms = methods.toArray(new MethodRepr[methods.size()]);
    Arrays.sort(ms, new Comparator<MethodRepr>() {
      @Override
      public int compare(final MethodRepr o1, final MethodRepr o2) {
        if (o1.name == o2.name) {
          final String d1 = o1.type.getDescr(context);
          final String d2 = o2.type.getDescr(context);

          final int c = d1.compareTo(d2);

          if (c == 0) {
            final int l1 = o1.argumentTypes.length;
            final int l2 = o2.argumentTypes.length;

            if (l1 == l2) {
              for (int i = 0; i<l1; i++) {
                final String d11 = o1.argumentTypes[i].getDescr(context);
                final String d22 = o2.argumentTypes[i].getDescr(context);

                final int cc = d11.compareTo(d22);

                if (cc != 0) {
                  return cc;
                }
              }

              return 0;
            }

            return l1 -l2;
          }

          return c;
        }

        return o1.name - o2.name;
      }
    });
    for (final MethodRepr m : ms) {
      m.toStream(context, stream);
    }
    stream.println("      End Of Methods");
  }
}
