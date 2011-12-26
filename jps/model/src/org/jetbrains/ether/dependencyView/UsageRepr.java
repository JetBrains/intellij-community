package org.jetbrains.ether.dependencyView;

import com.intellij.util.io.DataExternalizer;
import org.jetbrains.ether.RW;
import org.objectweb.asm.Type;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 14.02.11
 * Time: 5:11
 * To change this template use File | Settings | File Templates.
 */
class UsageRepr {
  private final static int FIELD_USAGE = 0;
  private final static int FIELD_ASSIGN_USAGE = 1;
  private final static int METHOD_USAGE = 2;
  private final static int CLASS_USAGE = 3;
  private final static int CLASS_EXTENDS_USAGE = 4;
  private final static int CLASS_NEW_USAGE = 5;
  private final static int ANNOTATION_USAGE = 6;
  private final static int METAMETHOD_USAGE = 7;

  private UsageRepr() {

  }

  public static class Cluster implements RW.Savable {
    final Set<Usage> usages = new HashSet<Usage>();
    final Map<Usage, Set<DependencyContext.S>> residentialMap = new HashMap<Usage, Set<DependencyContext.S>>();

    public Cluster() {
    }

    public Cluster(final DependencyContext context, final DataInput in) {
      try {
        final int size = in.readInt();

        for (int i = 0; i < size; i++) {
          final Usage u = externalizer(context).read(in);
          final Set<DependencyContext.S> s =
            (Set<DependencyContext.S>)RW.read(DependencyContext.descriptorS, new HashSet<DependencyContext.S>(), in);

          usages.add(u);
          residentialMap.put(u, s);
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeInt(usages.size());
        for (Usage u : usages) {
          u.save(out);
          RW.save(residentialMap.get(u), DependencyContext.descriptorS, out);
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    public void addUsage(final DependencyContext.S residence, final Usage usage) {
      Set<DependencyContext.S> s = residentialMap.get(usage);

      if (s == null) {
        s = new HashSet<DependencyContext.S>();
        residentialMap.put(usage, s);
      }

      s.add(residence);
      usages.add(usage);
    }

    public Set<Usage> getUsages() {
      return usages;
    }

    public Set<DependencyContext.S> getResidence(final Usage usage) {
      return residentialMap.get(usage);
    }

    public void updateCluster(final Cluster c) {
      usages.addAll(c.getUsages());
      for (Map.Entry<Usage, Set<DependencyContext.S>> e : c.residentialMap.entrySet()) {
        final Usage u = e.getKey();
        final Set<DependencyContext.S> v = e.getValue();
        final Set<DependencyContext.S> s = residentialMap.get(u);

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

    public static DataExternalizer<Cluster> clusterExternalizer(final DependencyContext context) {
      return new DataExternalizer<Cluster>() {
        @Override
        public void save(final DataOutput out, final Cluster value) throws IOException {
          value.save(out);
        }

        @Override
        public Cluster read(final DataInput in) throws IOException {
          return new Cluster(context, in);
        }
      };
    }
  }

  public static abstract class Usage implements RW.Savable {
    public abstract DependencyContext.S getOwner();
  }

  public static abstract class FMUsage extends Usage {
    public final DependencyContext.S name;
    public final DependencyContext.S owner;

    @Override
    public DependencyContext.S getOwner() {
      return owner;
    }

    private FMUsage(final DependencyContext.S n, final DependencyContext.S o) {
      name = n;
      owner = o;
    }

    private FMUsage(final DataInput in) {
      name = new DependencyContext.S(in);
      owner = new DependencyContext.S(in);
    }

    protected void save(final int tag, final DataOutput out) {
      try {
        out.writeInt(tag);
        name.save(out);
        owner.save(out);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      FMUsage fmUsage = (FMUsage)o;

      if (!name.equals(fmUsage.name)) return false;
      if (!owner.equals(fmUsage.owner)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = name.hashCode();
      result = 31 * result + owner.hashCode();
      return result;
    }
  }

  public static class FieldUsage extends FMUsage {
    public final TypeRepr.AbstractType type;

    private FieldUsage(final DependencyContext context,
                       final DependencyContext.S n,
                       final DependencyContext.S o,
                       final DependencyContext.S d) {
      super(n, o);
      type = TypeRepr.getType(context, d);
    }

    private FieldUsage(final DependencyContext context, final DataInput in) {
      super(in);
      try {
        type = TypeRepr.externalizer(context).read(in);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void save(final DataOutput out) {
      save(FIELD_USAGE, out);
      type.save(out);
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
    private FieldAssignUsage(final DependencyContext context,
                             final DependencyContext.S n,
                             final DependencyContext.S o,
                             final DependencyContext.S d) {
      super(context, n, o, d);
    }

    private FieldAssignUsage(final DependencyContext context, final DataInput in) {
      super(context, in);
    }

    @Override
    public void save(final DataOutput out) {
      save(FIELD_ASSIGN_USAGE, out);
      type.save(out);
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

    private MethodUsage(final DependencyContext context, final DependencyContext.S n, final DependencyContext.S o, final String d) {
      super(n, o);
      argumentTypes = TypeRepr.getType(context, Type.getArgumentTypes(d));
      returnType = TypeRepr.getType(context, Type.getReturnType(d));
    }

    private MethodUsage(final DependencyContext context, final DataInput in) {
      super(in);
      try {
        final DataExternalizer<TypeRepr.AbstractType> externalizer = TypeRepr.externalizer(context);
        argumentTypes = RW.read(externalizer, in, new TypeRepr.AbstractType[in.readInt()]);
        returnType = externalizer.read(in);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void save(final DataOutput out) {
      save(METHOD_USAGE, out);
      RW.save(argumentTypes, out);
      returnType.save(out);
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

  public static class MetaMethodUsage extends FMUsage {
    private int myArity;

    public MetaMethodUsage(final DependencyContext context, final DependencyContext.S n, final DependencyContext.S o, final String descr) {
      super(n, o);
      myArity = TypeRepr.getType(context, Type.getArgumentTypes(descr)).length;
    }

    public MetaMethodUsage(final DataInput in) {
      super(in);
      try {
        myArity = in.readInt();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void save(final DataOutput out) {
      super.save(METAMETHOD_USAGE, out);
      try {
        out.writeInt(myArity);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;

      MetaMethodUsage that = (MetaMethodUsage)o;

      if (myArity != that.myArity) return false;

      return super.equals(o);
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + myArity;
      return result;
    }
  }

  public static class ClassUsage extends Usage {
    final DependencyContext.S className;

    @Override
    public DependencyContext.S getOwner() {
      return className;
    }

    private ClassUsage(final DependencyContext.S n) {
      className = n;
    }

    private ClassUsage(final DataInput in) {
      className = new DependencyContext.S(in);
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeInt(CLASS_USAGE);
        className.save(out);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
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
    protected final DependencyContext.S className;

    @Override
    public DependencyContext.S getOwner() {
      return className;
    }

    private ClassExtendsUsage(final DependencyContext.S n) {
      className = n;
    }

    private ClassExtendsUsage(final DataInput in) {
      className = new DependencyContext.S(in);
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeInt(CLASS_EXTENDS_USAGE);
        className.save(out);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
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
    public ClassNewUsage(DependencyContext.S n) {
      super(n);
    }

    private ClassNewUsage(final DataInput in) {
      super(in);
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeInt(CLASS_NEW_USAGE);
        className.save(out);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public int hashCode() {
      return className.hashCode() + 2;
    }
  }

  public static class AnnotationUsage extends Usage {
    public static final DataExternalizer<ElementType> elementTypeExternalizer = new DataExternalizer<ElementType>() {
      @Override
      public void save(final DataOutput out, final ElementType value) throws IOException {
        out.writeUTF(value.toString());
      }

      @Override
      public ElementType read(final DataInput in) throws IOException {
        final String s = in.readUTF();
        return ElementType.valueOf(s);
      }
    };

    final TypeRepr.ClassType type;
    final Collection<DependencyContext.S> usedArguments;
    final Collection<ElementType> usedTargets;

    public boolean satisfies(final Usage usage) {
      if (usage instanceof AnnotationUsage) {
        final AnnotationUsage annotationUsage = (AnnotationUsage)usage;

        if (!type.equals(annotationUsage.type)) {
          return false;
        }

        boolean argumentsSatisfy = false;

        if (usedArguments != null) {
          final Collection<DependencyContext.S> arguments = new HashSet<DependencyContext.S>(usedArguments);

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
                            final Collection<DependencyContext.S> usedArguments,
                            final Collection<ElementType> targets) {
      this.type = type;
      this.usedArguments = usedArguments;
      this.usedTargets = targets;
    }

    private AnnotationUsage(final DependencyContext context, final DataInput in) {
      final DataExternalizer<TypeRepr.AbstractType> externalizer = TypeRepr.externalizer(context);

      try {
        type = (TypeRepr.ClassType)externalizer.read(in);
        usedArguments = RW.read(DependencyContext.descriptorS, new HashSet<DependencyContext.S>(), in);
        usedTargets = RW.read(elementTypeExternalizer, new HashSet<ElementType>(), in);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeInt(ANNOTATION_USAGE);
        type.save(out);
        RW.save(usedArguments, DependencyContext.descriptorS, out);
        RW.save(usedTargets, elementTypeExternalizer, out);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public DependencyContext.S getOwner() {
      return type.className;
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

  public static Usage createFieldUsage(final DependencyContext context,
                                       final DependencyContext.S name,
                                       final DependencyContext.S owner,
                                       final DependencyContext.S descr) {
    return context.getUsage(new FieldUsage(context, name, owner, descr));
  }

  public static Usage createFieldAssignUsage(final DependencyContext context,
                                             final DependencyContext.S name,
                                             final DependencyContext.S owner,
                                             final DependencyContext.S descr) {
    return context.getUsage(new FieldAssignUsage(context, name, owner, descr));
  }

  public static Usage createMethodUsage(final DependencyContext context,
                                        final DependencyContext.S name,
                                        final DependencyContext.S owner,
                                        final String descr) {
    return context.getUsage(new MethodUsage(context, name, owner, descr));
  }

  public static Usage createMetaMethodUsage(final DependencyContext context,
                                            final DependencyContext.S name,
                                            final DependencyContext.S owner,
                                            final String descr) {
    return context.getUsage(new MetaMethodUsage(context, name, owner, descr));
  }

  public static Usage createClassUsage(final DependencyContext context, final DependencyContext.S name) {
    return context.getUsage(new ClassUsage(name));
  }


  public static Usage createClassExtendsUsage(final DependencyContext context, final DependencyContext.S name) {
    return context.getUsage(new ClassExtendsUsage(name));
  }

  public static Usage createClassNewUsage(final DependencyContext context, final DependencyContext.S name) {
    return context.getUsage(new ClassNewUsage(name));
  }

  public static Usage createAnnotationUsage(final DependencyContext context,
                                            final TypeRepr.ClassType type,
                                            final Collection<DependencyContext.S> usedArguments,
                                            final Collection<ElementType> targets) {
    return context.getUsage(new AnnotationUsage(type, usedArguments, targets));
  }

  public static DataExternalizer<Usage> externalizer(final DependencyContext context) {
    return new DataExternalizer<Usage>() {
      @Override
      public void save(final DataOutput out, final Usage value) throws IOException {
        value.save(out);
      }

      @Override
      public Usage read(DataInput in) throws IOException {
        switch (in.readInt()) {
          case CLASS_USAGE:
            return context.getUsage(new ClassUsage(in));

          case CLASS_EXTENDS_USAGE:
            return context.getUsage(new ClassExtendsUsage(in));

          case CLASS_NEW_USAGE:
            return context.getUsage(new ClassNewUsage(in));

          case FIELD_USAGE:
            return context.getUsage(new FieldUsage(context, in));

          case FIELD_ASSIGN_USAGE:
            return context.getUsage(new FieldAssignUsage(context, in));

          case METHOD_USAGE:
            return context.getUsage(new MethodUsage(context, in));

          case ANNOTATION_USAGE:
            return context.getUsage(new AnnotationUsage(context, in));

          case METAMETHOD_USAGE:
            return context.getUsage(new MetaMethodUsage(in));
        }

        assert (false);

        return null;
      }
    };
  }
}
