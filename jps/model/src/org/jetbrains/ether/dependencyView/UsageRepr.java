package org.jetbrains.ether.dependencyView;

import com.intellij.util.io.DataExternalizer;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.asm4.Type;
import org.jetbrains.ether.RW;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 14.02.11
 * Time: 5:11
 * To change this template use File | Settings | File Templates.
 */
class UsageRepr {
  private static final byte FIELD_USAGE = 0x0;
  private static final byte FIELD_ASSIGN_USAGE = 0x1;
  private static final byte METHOD_USAGE = 0x2;
  private static final byte CLASS_USAGE = 0x3;
  private static final byte CLASS_EXTENDS_USAGE = 0x4;
  private static final byte CLASS_NEW_USAGE = 0x5;
  private static final byte ANNOTATION_USAGE = 0x6;
  private static final byte METAMETHOD_USAGE = 0x7;

  private static final int DEFAULT_SET_CAPACITY = 32;
  private static final float DEFAULT_SET_LOAD_FACTOR = 0.98f;

  private UsageRepr() {

  }

  public static class Cluster implements RW.Savable {
    private final Map<Usage, TIntHashSet> myUsageToDependenciesMap = new HashMap<Usage, TIntHashSet>(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);

    public Cluster() {
    }

    public Cluster(final DependencyContext context, final DataInput in) {
      try {
        final int size = in.readInt();

        for (int i = 0; i < size; i++) {
          final Usage u = externalizer(context).read(in);
          final TIntHashSet s = RW.read(new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR), in);
          myUsageToDependenciesMap.put(u, s);
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeInt(myUsageToDependenciesMap.size());
        for (Map.Entry<Usage, TIntHashSet> entry : myUsageToDependenciesMap.entrySet()) {
          final Usage u = entry.getKey();
          u.save(out);
          final TIntHashSet deps = entry.getValue();
          RW.save(deps, out);
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    public void addUsage(final int residence, final Usage usage) {
      TIntHashSet s = myUsageToDependenciesMap.get(usage);

      if (s == null) {
        s = new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
        myUsageToDependenciesMap.put(usage, s);
      }

      s.add(residence);
    }

    @NotNull
    public Set<Usage> getUsages() {
      return Collections.unmodifiableSet(myUsageToDependenciesMap.keySet());
    }

    public TIntHashSet getResidence(final Usage usage) {
      return myUsageToDependenciesMap.get(usage);
    }

    public boolean isEmpty() {
      return myUsageToDependenciesMap.isEmpty();
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

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Cluster cluster = (Cluster)o;

      if (!myUsageToDependenciesMap.equals(cluster.myUsageToDependenciesMap)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myUsageToDependenciesMap.hashCode();
    }
  }

  public static abstract class Usage implements RW.Savable {
    public abstract int getOwner();
  }

  public static abstract class FMUsage extends Usage {
    public final int name;
    public final int owner;

    @Override
    public int getOwner() {
      return owner;
    }

    private FMUsage(final int name, final int owner) {
      this.name = name;
      this.owner = owner;
    }

    private FMUsage(final DataInput in) {
      try {
        name = in.readInt();
        owner = in.readInt();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    protected final void save(final byte tag, final DataOutput out) {
      try {
        out.writeByte(tag);
        out.writeInt(name);
        out.writeInt(owner);
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

      if (name != fmUsage.name) return false;
      if (owner != fmUsage.owner) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return 31 * name + owner;
    }
  }

  public static class FieldUsage extends FMUsage {
    public final TypeRepr.AbstractType type;

    private FieldUsage(final DependencyContext context, final int name, final int owner, final int descriptor) {
      super(name, owner);
      type = TypeRepr.getType(context, descriptor);
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

      return type.equals(that.type) && name == that.name && owner == that.owner;
    }

    @Override
    public int hashCode() {
      return 31 * (31 * type.hashCode() + name) + owner;
    }
  }

  public static class FieldAssignUsage extends FieldUsage {
    private FieldAssignUsage(final DependencyContext context, final int n, final int o, final int d) {
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

      return type.equals(that.type) && name == that.name && owner == that.owner;
    }

    @Override
    public int hashCode() {
      return super.hashCode() + 1;
    }
  }

  public static class MethodUsage extends FMUsage {
    public final TypeRepr.AbstractType[] argumentTypes;
    public final TypeRepr.AbstractType returnType;

    private MethodUsage(final DependencyContext context, final int name, final int owner, final String descriptor) {
      super(name, owner);
      argumentTypes = TypeRepr.getType(context, Type.getArgumentTypes(descriptor));
      returnType = TypeRepr.getType(context, Type.getReturnType(descriptor));
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
      if (name != that.name) return false;
      if (owner != that.owner) return false;

      return Arrays.equals(argumentTypes, that.argumentTypes) &&
             returnType.equals(that.returnType) &&
             name == that.name &&
             owner == that.owner;
    }

    @Override
    public int hashCode() {
      return ((31 * Arrays.hashCode(argumentTypes) + (returnType.hashCode())) * 31 + (name)) * 31 + (owner);
    }
  }

  public static class MetaMethodUsage extends FMUsage {
    private int myArity;

    public MetaMethodUsage(final DependencyContext context, final int n, final int o, final String descr) {
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
      save(METAMETHOD_USAGE, out);
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
    final int myClassName;

    @Override
    public int getOwner() {
      return myClassName;
    }

    private ClassUsage(final int className) {
      this.myClassName = className;
    }

    private ClassUsage(final DataInput in) {
      try {
        myClassName = in.readInt();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeByte(CLASS_USAGE);
        out.writeInt(myClassName);
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

      return myClassName == that.myClassName;
    }

    @Override
    public int hashCode() {
      return myClassName;
    }
  }

  public static class ClassExtendsUsage extends Usage {
    protected final int className;

    @Override
    public int getOwner() {
      return className;
    }

    private ClassExtendsUsage(final int className) {
      this.className = className;
    }

    private ClassExtendsUsage(final DataInput in) {
      try {
        className = in.readInt();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeByte(CLASS_EXTENDS_USAGE);
        out.writeInt(className);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public int hashCode() {
      return className + 1;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ClassExtendsUsage that = (ClassExtendsUsage)o;

      if (className != that.className) return false;

      return true;
    }
  }

  public static class ClassNewUsage extends ClassExtendsUsage {
    public ClassNewUsage(int className) {
      super(className);
    }

    private ClassNewUsage(final DataInput in) {
      super(in);
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeByte(CLASS_NEW_USAGE);
        out.writeInt(className);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public int hashCode() {
      return className + 2;
    }
  }

  public static class AnnotationUsage extends Usage {
    public static final DataExternalizer<ElemType> elementTypeExternalizer = new DataExternalizer<ElemType>() {
      @Override
      public void save(final DataOutput out, final ElemType value) throws IOException {
        out.writeInt(value.ordinal());
      }

      @Override
      public ElemType read(final DataInput in) throws IOException {
        final int ordinal = in.readInt();
        for (ElemType value : ElemType.values()) {
          if (value.ordinal() == ordinal) {
            return value;
          }
        }
        throw new IOException("Error reading ElementType enum value; unknown ordinal: " + ordinal);
      }
    };

    final TypeRepr.ClassType type;
    final TIntHashSet usedArguments;
    final Set<ElemType> usedTargets;

    public boolean satisfies(final Usage usage) {
      if (usage instanceof AnnotationUsage) {
        final AnnotationUsage annotationUsage = (AnnotationUsage)usage;

        if (!type.equals(annotationUsage.type)) {
          return false;
        }

        boolean argumentsSatisfy = false;

        if (usedArguments != null) {
          final TIntHashSet arguments = new TIntHashSet(usedArguments.toArray());

          arguments.removeAll(annotationUsage.usedArguments.toArray());

          argumentsSatisfy = !arguments.isEmpty();
        }

        boolean targetsSatisfy = false;

        if (usedTargets != null) {
          final Collection<ElemType> targets = EnumSet.copyOf(usedTargets);

          targets.retainAll(annotationUsage.usedTargets);

          targetsSatisfy = !targets.isEmpty();
        }

        return argumentsSatisfy || targetsSatisfy;
      }

      return false;
    }

    private AnnotationUsage(final TypeRepr.ClassType type, final TIntHashSet usedArguments, final Set<ElemType> targets) {
      this.type = type;
      this.usedArguments = usedArguments;
      this.usedTargets = targets;
    }

    private AnnotationUsage(final DependencyContext context, final DataInput in) {
      final DataExternalizer<TypeRepr.AbstractType> externalizer = TypeRepr.externalizer(context);

      try {
        type = (TypeRepr.ClassType)externalizer.read(in);
        usedArguments = RW.read(new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR), in);
        usedTargets = (EnumSet<ElemType>)RW.read(elementTypeExternalizer, EnumSet.noneOf(ElemType.class), in);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeByte(ANNOTATION_USAGE);
        type.save(out);
        RW.save(usedArguments, out);
        RW.save(usedTargets, elementTypeExternalizer, out);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public int getOwner() {
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

  public static Usage createFieldUsage(final DependencyContext context, final int name, final int owner, final int descr) {
    return context.getUsage(new FieldUsage(context, name, owner, descr));
  }

  public static Usage createFieldAssignUsage(final DependencyContext context, final int name, final int owner, final int descr) {
    return context.getUsage(new FieldAssignUsage(context, name, owner, descr));
  }

  public static Usage createMethodUsage(final DependencyContext context, final int name, final int owner, final String descr) {
    return context.getUsage(new MethodUsage(context, name, owner, descr));
  }

  public static Usage createMetaMethodUsage(final DependencyContext context, final int name, final int owner, final String descr) {
    return context.getUsage(new MetaMethodUsage(context, name, owner, descr));
  }

  public static Usage createClassUsage(final DependencyContext context, final int name) {
    return context.getUsage(new ClassUsage(name));
  }


  public static Usage createClassExtendsUsage(final DependencyContext context, final int name) {
    return context.getUsage(new ClassExtendsUsage(name));
  }

  public static Usage createClassNewUsage(final DependencyContext context, final int name) {
    return context.getUsage(new ClassNewUsage(name));
  }

  public static Usage createAnnotationUsage(final DependencyContext context, final TypeRepr.ClassType type, final TIntHashSet usedArguments, final Set<ElemType> targets) {
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
        final byte tag = in.readByte();
        switch (tag) {
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
