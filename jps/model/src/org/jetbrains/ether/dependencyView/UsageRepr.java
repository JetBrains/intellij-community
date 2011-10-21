package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.RW;
import org.objectweb.asm.Type;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.annotation.ElementType;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 14.02.11
 * Time: 5:11
 * To change this template use File | Settings | File Templates.
 */
public class UsageRepr {
  private final static TypeRepr.AbstractType[] dummyAbstractType = new TypeRepr.AbstractType[0];

  private final static Map<Usage, Usage> map = new HashMap<Usage, Usage>();

  private static Usage getUsage(final Usage u) {
    final Usage r = map.get(u);

    if (r == null) {
      map.put(u, u);
      return u;
    }

    return r;
  }

  public static class Cluster implements RW.Writable {
    final Set<Usage> usages = new HashSet<Usage>();
    final Map<Usage, Set<StringCache.S>> residentialMap = new HashMap<Usage, Set<StringCache.S>>();

    public Cluster() {
    }

    public Cluster(final BufferedReader r) {
      final int size = RW.readInt(r);

      for (int i = 0; i < size; i++) {
        final Usage u = reader.read(r);
        final Set<StringCache.S> s = (Set<StringCache.S>)RW.readMany(r, StringCache.reader, new HashSet<StringCache.S>());

        usages.add(u);
        residentialMap.put(u, s);
      }
    }

    public void write(final BufferedWriter w) {
      RW.writeln(w, Integer.toString(usages.size()));

      for (Usage u : usages) {
        u.write(w);
        RW.writeln(w, residentialMap.get(u));
      }
    }

    public void addUsage(final String residence, final Usage usage) {
      final StringCache.S r = StringCache.get(residence);

      Set<StringCache.S> s = residentialMap.get(usage);

      if (s == null) {
        s = new HashSet<StringCache.S>();
        residentialMap.put(usage, s);
      }

      s.add(r);
      usages.add(usage);
    }

    public Set<Usage> getUsages() {
      return usages;
    }

    public Set<StringCache.S> getResidence(final Usage usage) {
      return residentialMap.get(usage);
    }

    public void updateCluster(final Cluster c) {
      usages.addAll(c.getUsages());
      for (Map.Entry<Usage, Set<StringCache.S>> e : c.residentialMap.entrySet()) {
        final Usage u = e.getKey();
        final Set<StringCache.S> v = e.getValue();
        final Set<StringCache.S> s = residentialMap.get(u);

        if (s == null) {
          residentialMap.put(u, v);
        }
        else {
          s.addAll(v);
        }
      }
    }

    public boolean isEmpty() {
      return usages.isEmpty();
    }
  }

  public static abstract class Usage implements RW.Writable {
    public abstract StringCache.S getOwner();
  }

  public static abstract class FMUsage extends Usage {
    public final StringCache.S name;
    public final StringCache.S owner;

    @Override
    public StringCache.S getOwner() {
      return owner;
    }

    protected FMUsage(final String n, final String o) {
      name = StringCache.get(n);
      owner = StringCache.get(o);
    }

    protected FMUsage(final BufferedReader r) {
      name = StringCache.get(RW.readString(r));
      owner = StringCache.get(RW.readString(r));
    }
  }

  public static class FieldUsage extends FMUsage {
    public final TypeRepr.AbstractType type;

    private FieldUsage(final String n, final String o, final String d) {
      super(n, o);
      type = TypeRepr.getType(d);
    }

    private FieldUsage(final BufferedReader r) {
      super(r);
      type = TypeRepr.reader.read(r);
    }

    public void write(final BufferedWriter w) {
      RW.writeln(w, "fieldUsage");
      RW.writeln(w, name.value);
      RW.writeln(w, owner.value);
      type.write(w);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final FieldUsage that = (FieldUsage)o;

      return type.equals(that.type) && name.equals(that.name) && owner.equals(that.owner);
    }

    @Override
    public int hashCode() {
      return 31 * (31 * type.hashCode() + (name.hashCode())) + owner.hashCode();
    }
  }

  public static class FieldAssignUsage extends FieldUsage {
    private FieldAssignUsage(final String n, final String o, final String d) {
      super(n, o, d);
    }

    private FieldAssignUsage(final BufferedReader r) {
      super(r);
    }

    public void write(final BufferedWriter w) {
      RW.writeln(w, "fieldAssignUsage");
      RW.writeln(w, name.value);
      RW.writeln(w, owner.value);
      type.write(w);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final FieldAssignUsage that = (FieldAssignUsage)o;

      return type.equals(that.type) && name.equals(that.name) && owner.equals(that.owner);
    }

    @Override
    public int hashCode() {
      return super.hashCode() + 1;
    }
  }

  public static class MethodUsage extends FMUsage {
    public final TypeRepr.AbstractType[] argumentTypes;
    public final TypeRepr.AbstractType returnType;

    private MethodUsage(final String n, final String o, final String d) {
      super(n, o);
      argumentTypes = TypeRepr.getType(Type.getArgumentTypes(d));
      returnType = TypeRepr.getType(Type.getReturnType(d));
    }

    private MethodUsage(final BufferedReader r) {
      super(r);
      argumentTypes = RW.readMany(r, TypeRepr.reader, new ArrayList<TypeRepr.AbstractType>()).toArray(dummyAbstractType);
      returnType = TypeRepr.reader.read(r);
    }

    public void write(final BufferedWriter w) {
      RW.writeln(w, "methodUsage");
      RW.writeln(w, name.value);
      RW.writeln(w, owner.value);
      RW.writeln(w, argumentTypes, TypeRepr.fromAbstractType);
      returnType.write(w);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final MethodUsage that = (MethodUsage)o;

      if (!Arrays.equals(argumentTypes, that.argumentTypes)) return false;
      if (returnType != null ? !returnType.equals(that.returnType) : that.returnType != null) return false;
      if (name != null ? !name.equals(that.name) : that.name != null) return false;
      if (owner != null ? !owner.equals(that.owner) : that.owner != null) return false;

      return Arrays.equals(argumentTypes, that.argumentTypes) &&
             returnType.equals(that.returnType) &&
             name.equals(that.name) &&
             owner.equals(that.owner);
    }

    @Override
    public int hashCode() {
      return ((31 * Arrays.hashCode(argumentTypes) + (returnType.hashCode())) * 31 + (name.hashCode())) * 31 + (owner.hashCode());
    }
  }

  public static class ClassUsage extends Usage {
    final StringCache.S className;

    @Override
    public StringCache.S getOwner() {
      return className;
    }

    private ClassUsage(final String n) {
      className = StringCache.get(n);
    }

    private ClassUsage(final StringCache.S n) {
      className = n;
    }

    private ClassUsage(final BufferedReader r) {
      className = StringCache.get(RW.readString(r));
    }

    private ClassUsage(final BufferedReader r, boolean b) {
      super();
      className = StringCache.get(RW.readString(r));
    }

    public void write(final BufferedWriter w) {
      RW.writeln(w, "classUsage");
      RW.writeln(w, className.value);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ClassUsage that = (ClassUsage)o;

      return className.equals(that.className);
    }

    @Override
    public int hashCode() {
      return className.hashCode();
    }
  }

  public static class ClassExtendsUsage extends Usage {
    protected final StringCache.S className;

    @Override
    public StringCache.S getOwner() {
      return className;
    }

    public ClassExtendsUsage(final StringCache.S n) {
      className = n;
    }

    public ClassExtendsUsage(final BufferedReader r) {
      className = StringCache.get(RW.readString(r));
    }

    public void write(final BufferedWriter w) {
      RW.writeln(w, "classExtendsUsage");
      RW.writeln(w, className.value);
    }

    @Override
    public int hashCode() {
      return className.hashCode() + 1;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ClassExtendsUsage that = (ClassExtendsUsage)o;

      if (!className.equals(that.className)) return false;

      return true;
    }
  }

  public static class ClassNewUsage extends ClassExtendsUsage {
    public ClassNewUsage(StringCache.S n) {
      super(n);
    }

    public ClassNewUsage(BufferedReader r) {
      super(r);
    }

    public void write(final BufferedWriter w) {
      RW.writeln(w, "classNewUsage");
      RW.writeln(w, className.value);
    }

    @Override
    public int hashCode() {
      return className.hashCode() + 2;
    }
  }

  public static class AnnotationUsage extends Usage {
    public static final RW.Reader<ElementType> elementTypeReader = new RW.Reader<ElementType>() {
      public ElementType read(final BufferedReader r) {
        return ElementType.valueOf(RW.readString(r));
      }
    };

    public static final RW.ToWritable<ElementType> elementTypeToWritable = new RW.ToWritable<ElementType>() {
      public RW.Writable convert(final ElementType x) {
        return new RW.Writable() {
          public void write(final BufferedWriter w) {
            RW.writeln(w, x.toString());
          }
        };
      }
    };

    final TypeRepr.ClassType type;
    final Collection<StringCache.S> usedArguments;
    final Collection<ElementType> usedTargets;

    public boolean satisfies(final Usage usage) {
      if (usage instanceof AnnotationUsage) {
        final AnnotationUsage annotationUsage = (AnnotationUsage)usage;

        if (!type.equals(annotationUsage.type)) {
          return false;
        }

        boolean argumentsSatisfy = false;

        if (usedArguments != null) {
          final Collection<StringCache.S> arguments = new HashSet<StringCache.S>(usedArguments);

          arguments.removeAll(annotationUsage.usedArguments);

          argumentsSatisfy = !arguments.isEmpty();
        }

        boolean targetsSatisfy = false;

        if (usedTargets != null) {
          final Collection<ElementType> targets = new HashSet<ElementType>(usedTargets);

          targets.retainAll(annotationUsage.usedTargets);

          targetsSatisfy = !targets.isEmpty();
        }

        return argumentsSatisfy || targetsSatisfy;
      }

      return false;
    }

    private AnnotationUsage(final TypeRepr.ClassType type,
                            final Collection<StringCache.S> usedArguments,
                            final Collection<ElementType> targets) {
      this.type = type;
      this.usedArguments = usedArguments;
      this.usedTargets = targets;
    }

    private AnnotationUsage(final BufferedReader r) {
      type = (TypeRepr.ClassType)TypeRepr.reader.read(r);
      usedArguments = RW.readMany(r, StringCache.reader, new HashSet<StringCache.S>());
      usedTargets = RW.readMany(r, elementTypeReader, new HashSet<ElementType>());
    }

    @Override
    public StringCache.S getOwner() {
      return type.className;
    }

    public void write(final BufferedWriter w) {
      RW.writeln(w, "annotationUsage");
      type.write(w);
      RW.writeln(w, usedArguments);
      RW.writeln(w, usedTargets, elementTypeToWritable);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AnnotationUsage that = (AnnotationUsage)o;

      if (usedArguments != null ? !usedArguments.equals(that.usedArguments) : that.usedArguments != null) return false;
      if (usedTargets != null ? !usedTargets.equals(that.usedTargets) : that.usedTargets != null) return false;
      if (type != null ? !type.equals(that.type) : that.type != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = type != null ? type.hashCode() : 0;
      result = 31 * result + (usedArguments != null ? usedArguments.hashCode() : 0);
      result = 31 * result + (usedTargets != null ? usedTargets.hashCode() : 0);
      return result;
    }
  }

  public static Usage createFieldUsage(final String name, final String owner, final String descr) {
    return getUsage(new FieldUsage(name, owner, descr));
  }

  public static Usage createFieldAssignUsage(final String name, final String owner, final String descr) {
    return getUsage(new FieldAssignUsage(name, owner, descr));
  }

  public static Usage createMethodUsage(final String name, final String owner, final String descr) {
    return getUsage(new MethodUsage(name, owner, descr));
  }

  public static Usage createClassUsage(final String name) {
    return getUsage(new ClassUsage(name));
  }

  public static Usage createClassUsage(final StringCache.S name) {
    return getUsage(new ClassUsage(name));
  }

  public static Usage createClassExtendsUsage(final StringCache.S name) {
    return getUsage(new ClassExtendsUsage(name));
  }

  public static Usage createClassNewUsage(final StringCache.S name) {
    return getUsage(new ClassNewUsage(name));
  }

  public static Usage createAnnotationUsage(final TypeRepr.ClassType type,
                                            final Collection<StringCache.S> usedArguments,
                                            final Collection<ElementType> targets) {
    return getUsage(new AnnotationUsage(type, usedArguments, targets));
  }

  public static RW.Reader<Usage> reader = new RW.Reader<Usage>() {
    public Usage read(final BufferedReader r) {
      final String tag = RW.readString(r);

      if (tag.equals("classUsage")) {
        return getUsage(new ClassUsage(r));
      }
      else if (tag.equals("fieldUsage")) {
        return getUsage(new FieldUsage(r));
      }
      else if (tag.equals("fieldAssignUsage")) {
        return getUsage(new FieldAssignUsage(r));
      }
      else if (tag.equals("methodUsage")) {
        return getUsage(new MethodUsage(r));
      }
      else if (tag.equals("classExtendsUsage")) {
        return getUsage(new ClassExtendsUsage(r));
      }
      else if (tag.equals("classNewUsage")) {
        return getUsage(new ClassNewUsage(r));
      }
      else {
        return getUsage(new AnnotationUsage(r));
      }
    }
  };

  public static RW.Reader<Cluster> clusterReader = new RW.Reader<Cluster>() {
    @Override
    public Cluster read(BufferedReader r) {
      return new Cluster(r);
    }
  };
}
