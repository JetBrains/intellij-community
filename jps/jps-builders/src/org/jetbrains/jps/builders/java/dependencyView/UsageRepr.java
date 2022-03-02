// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.org.objectweb.asm.Type;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

/**
 * @author: db
 */
final class UsageRepr {
  private static final byte FIELD_USAGE = 0x0;
  private static final byte FIELD_ASSIGN_USAGE = 0x1;
  private static final byte METHOD_USAGE = 0x2;
  private static final byte CLASS_USAGE = 0x3;
  private static final byte CLASS_EXTENDS_USAGE = 0x4;
  private static final byte CLASS_NEW_USAGE = 0x5;
  private static final byte ANNOTATION_USAGE = 0x6;
  private static final byte METAMETHOD_USAGE = 0x7;
  private static final byte CLASS_AS_GENERIC_BOUND_USAGE = 0x8;
  private static final byte MODULE_USAGE = 0x9;
  private static final byte IMPORT_STATIC_MEMBER_USAGE = 0xa;
  private static final byte IMPORT_STATIC_ON_DEMAND_USAGE = 0xb;

  private static final int DEFAULT_SET_CAPACITY = 32;
  private static final float DEFAULT_SET_LOAD_FACTOR = 0.98f;

  private UsageRepr() {

  }

  public static abstract class Usage implements RW.Savable, Streamable {
    public abstract int getOwner();
  }

  public static abstract class FMUsage extends Usage {
    public final int myName;
    public final int myOwner;

    abstract void kindToStream (PrintStream stream);

    @Override
    public void toStream(final DependencyContext context, final PrintStream stream) {
      kindToStream(stream);
      stream.println("          Name : " + context.getValue(myName));
      stream.println("          Owner: " + context.getValue(myOwner));
    }

    @Override
    public int getOwner() {
      return myOwner;
    }

    private FMUsage(final int name, final int owner) {
      this.myName = name;
      this.myOwner = owner;
    }

    private FMUsage(final DataInput in) {
      try {
        myName = DataInputOutputUtil.readINT(in);
        myOwner = DataInputOutputUtil.readINT(in);
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }

    protected final void save(final byte tag, final DataOutput out) {
      try {
        out.writeByte(tag);
        DataInputOutputUtil.writeINT(out, myName);
        DataInputOutputUtil.writeINT(out, myOwner);
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      FMUsage fmUsage = (FMUsage)o;

      if (myName != fmUsage.myName) return false;
      if (myOwner != fmUsage.myOwner) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return 31 * myName + myOwner;
    }
  }

  public static class FieldUsage extends FMUsage {
    public final TypeRepr.AbstractType myType;

    private FieldUsage(final DependencyContext context, final int name, final int owner, final int descriptor) {
      super(name, owner);
      myType = TypeRepr.getType(context, descriptor);
    }

    private FieldUsage(final DependencyContext context, final DataInput in) {
      super(in);
      try {
        myType = TypeRepr.externalizer(context).read(in);
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }

    @Override
    protected void kindToStream(final PrintStream stream) {
      stream.println("FieldUsage:");
    }

    @Override
    public void toStream(final DependencyContext context, final PrintStream stream) {
      super.toStream(context, stream);
      stream.println("          Type: " + myType.getDescr(context));
    }

    @Override
    public void save(final DataOutput out) {
      save(FIELD_USAGE, out);
      myType.save(out);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final FieldUsage that = (FieldUsage)o;

      return myType.equals(that.myType) && myName == that.myName && myOwner == that.myOwner;
    }

    @Override
    public int hashCode() {
      return 31 * (31 * myType.hashCode() + myName) + myOwner;
    }
  }

  public static final class FieldAssignUsage extends FieldUsage {
    private FieldAssignUsage(final DependencyContext context, final int n, final int o, final int d) {
      super(context, n, o, d);
    }

    private FieldAssignUsage(final DependencyContext context, final DataInput in) {
      super(context, in);
    }

    @Override
    protected void kindToStream(final PrintStream stream) {
      stream.println("FieldAssignUsage:");
    }

    @Override
    public void save(final DataOutput out) {
      save(FIELD_ASSIGN_USAGE, out);
      myType.save(out);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final FieldAssignUsage that = (FieldAssignUsage)o;

      return myType.equals(that.myType) && myName == that.myName && myOwner == that.myOwner;
    }

    @Override
    public int hashCode() {
      return super.hashCode() + 1;
    }
  }

  public static final class MethodUsage extends FMUsage {
    public final TypeRepr.AbstractType[] myArgumentTypes;
    public final TypeRepr.AbstractType myReturnType;

    private MethodUsage(final DependencyContext context, final int name, final int owner, final String descriptor) {
      super(name, owner);
      try {
        myArgumentTypes = TypeRepr.getType(context, Type.getArgumentTypes(descriptor));
        myReturnType = TypeRepr.getType(context, Type.getReturnType(descriptor));
      }
      catch (IllegalArgumentException e) {
        throw (BuildDataCorruptedException)new BuildDataCorruptedException("Unexpected method descriptor '" + descriptor + "'").initCause(e);
      }
    }

    private MethodUsage(final DependencyContext context, final DataInput in) {
      super(in);
      try {
        final DataExternalizer<TypeRepr.AbstractType> externalizer = TypeRepr.externalizer(context);
        int argumentTypes = DataInputOutputUtil.readINT(in);
        myArgumentTypes = RW.read(externalizer, in, argumentTypes != 0 ? new TypeRepr.AbstractType[argumentTypes]: TypeRepr.AbstractType.EMPTY_TYPE_ARRAY);
        myReturnType = externalizer.read(in);
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }

    @Override
    public void save(final DataOutput out) {
      save(METHOD_USAGE, out);
      RW.save(myArgumentTypes, out);
      myReturnType.save(out);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final MethodUsage that = (MethodUsage)o;

      if (myReturnType != null ? !myReturnType.equals(that.myReturnType) : that.myReturnType != null) return false;
      if (myName != that.myName) return false;
      if (myOwner != that.myOwner) return false;

      return Arrays.equals(myArgumentTypes, that.myArgumentTypes);
    }

    @Override
    public int hashCode() {
      return ((31 * Arrays.hashCode(myArgumentTypes) + (myReturnType.hashCode())) * 31 + (myName)) * 31 + (myOwner);
    }

    @Override
    void kindToStream(final PrintStream stream) {
      stream.println("MethodUsage:");
    }

    @Override
    public void toStream(DependencyContext context, PrintStream stream) {
      super.toStream(context, stream);

      stream.println("          Arguments:");

      for (final TypeRepr.AbstractType at : myArgumentTypes) {
        stream.println("            " + at.getDescr(context));
      }

      stream.println("          Return type:");
      stream.println("            " + myReturnType.getDescr(context));
    }
  }

  public static class MetaMethodUsage extends FMUsage {

    public MetaMethodUsage(final int n, final int o) {
      super(n, o);
    }

    public MetaMethodUsage(final DataInput in) {
      super(in);
    }

    @Override
    public void save(final DataOutput out) {
      save(METAMETHOD_USAGE, out);
    }

    @Override
    void kindToStream(final PrintStream stream) {
      stream.println("MetaMethodUsage:");
    }
  }

  public static class ImportStaticMemberUsage extends FMUsage {

    public ImportStaticMemberUsage(final int n, final int o) {
      super(n, o);
    }

    public ImportStaticMemberUsage(final DataInput in) {
      super(in);
    }

    @Override
    public void save(final DataOutput out) {
      save(IMPORT_STATIC_MEMBER_USAGE, out);
    }

    @Override
    void kindToStream(final PrintStream stream) {
      stream.println("ImportStaticMemberUsage:");
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
        myClassName = DataInputOutputUtil.readINT(in);
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeByte(CLASS_USAGE);
        DataInputOutputUtil.writeINT(out, myClassName);
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
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

    @Override
    public void toStream(final DependencyContext context, final PrintStream stream) {
      stream.println("ClassUsage: " + context.getValue(myClassName));
    }
  }

  public static final class ModuleUsage extends Usage {
    final int myModuleName;

    @Override
    public int getOwner() {
      return myModuleName;
    }

    private ModuleUsage(final int moduleName) {
      this.myModuleName = moduleName;
    }

    private ModuleUsage(final DataInput in) {
      try {
        myModuleName = DataInputOutputUtil.readINT(in);
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeByte(MODULE_USAGE);
        DataInputOutputUtil.writeINT(out, myModuleName);
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ModuleUsage that = (ModuleUsage)o;

      return myModuleName == that.myModuleName;
    }

    @Override
    public int hashCode() {
      return myModuleName;
    }

    @Override
    public void toStream(final DependencyContext context, final PrintStream stream) {
      stream.println("ModuleUsage: " + context.getValue(myModuleName));
    }
  }

  public static final class ImportStaticOnDemandUsage extends Usage {
    final int myOwner; // owner class

    @Override
    public int getOwner() {
      return myOwner;
    }

    private ImportStaticOnDemandUsage(final int owner) {
      this.myOwner = owner;
    }

    private ImportStaticOnDemandUsage(final DataInput in) {
      try {
        myOwner = DataInputOutputUtil.readINT(in);
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeByte(IMPORT_STATIC_ON_DEMAND_USAGE);
        DataInputOutputUtil.writeINT(out, myOwner);
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ImportStaticOnDemandUsage that = (ImportStaticOnDemandUsage)o;

      return myOwner == that.myOwner;
    }

    @Override
    public int hashCode() {
      return myOwner;
    }

    @Override
    public void toStream(final DependencyContext context, final PrintStream stream) {
      stream.println("ImportStaticOnDemandUsage: " + context.getValue(myOwner));
    }
  }

  public static class ClassAsGenericBoundUsage extends ClassUsage {
    public ClassAsGenericBoundUsage(int className) {
      super(className);
    }

    public ClassAsGenericBoundUsage(DataInput in) {
      super(in);
    }

    @Override
    public int hashCode() {
      return super.hashCode() + 3;
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeByte(CLASS_AS_GENERIC_BOUND_USAGE);
        DataInputOutputUtil.writeINT(out, myClassName);
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }

    @Override
    public void toStream(final DependencyContext context, final PrintStream stream) {
      stream.println("ClassAsGenericBoundUsage: " + context.getValue(myClassName));
    }
  }

  public static class ClassExtendsUsage extends Usage {
    protected final int myClassName;

    @Override
    public int getOwner() {
      return myClassName;
    }

    private ClassExtendsUsage(final int className) {
      this.myClassName = className;
    }

    private ClassExtendsUsage(final DataInput in) {
      try {
        myClassName = DataInputOutputUtil.readINT(in);
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeByte(CLASS_EXTENDS_USAGE);
        DataInputOutputUtil.writeINT(out, myClassName);
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }

    @Override
    public int hashCode() {
      return myClassName + 1;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ClassExtendsUsage that = (ClassExtendsUsage)o;

      if (myClassName != that.myClassName) return false;

      return true;
    }

    @Override
    public void toStream(final DependencyContext context, final PrintStream stream) {
      stream.println("ClassExtendsUsage: " + context.getValue(myClassName));
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
        DataInputOutputUtil.writeINT(out, myClassName);
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }

    @Override
    public int hashCode() {
      return myClassName + 2;
    }

    @Override
    public void toStream(final DependencyContext context, final PrintStream stream) {
      stream.println("ClassNewUsage: " + context.getValue(myClassName));
    }
  }

  public static final class AnnotationUsage extends Usage {
    public static final DataExternalizer<ElemType> elementTypeExternalizer = new DataExternalizer<ElemType>() {
      @Override
      public void save(@NotNull final DataOutput out, final ElemType value) throws IOException {
        DataInputOutputUtil.writeINT(out, value.ordinal());
      }

      @Override
      public ElemType read(@NotNull final DataInput in) throws IOException {
        final int ordinal = DataInputOutputUtil.readINT(in);
        for (ElemType value : ElemType.values()) {
          if (value.ordinal() == ordinal) {
            return value;
          }
        }
        throw new IOException("Error reading ElementType enum value; unknown ordinal: " + ordinal);
      }
    };

    final TypeRepr.ClassType myType;
    final IntSet myUsedArguments;
    final Set<ElemType> myUsedTargets;

    public boolean satisfies(final AnnotationUsage annotationUsage) {
      if (!myType.equals(annotationUsage.myType)) {
        return false;
      }

      boolean argumentsSatisfy = false;

      if (myUsedArguments != null) {
        IntSet arguments = new IntOpenHashSet(myUsedArguments);
        arguments.removeAll(annotationUsage.myUsedArguments);
        argumentsSatisfy = !arguments.isEmpty();
      }

      boolean targetsSatisfy = false;

      if (myUsedTargets != null) {
        final Collection<ElemType> targets = EnumSet.copyOf(myUsedTargets);

        targets.retainAll(annotationUsage.myUsedTargets);

        targetsSatisfy = !targets.isEmpty();
      }

      return argumentsSatisfy || targetsSatisfy;
    }

    private AnnotationUsage(final TypeRepr.ClassType type, final IntSet usedArguments, final Set<ElemType> targets) {
      this.myType = type;
      this.myUsedArguments = usedArguments;
      this.myUsedTargets = targets;
    }

    private AnnotationUsage(final DependencyContext context, final DataInput in) {
      final DataExternalizer<TypeRepr.AbstractType> externalizer = TypeRepr.externalizer(context);

      try {
        myType = (TypeRepr.ClassType)externalizer.read(in);
        myUsedArguments = RW.read(new IntOpenHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR), in);
        myUsedTargets = RW.read(elementTypeExternalizer, EnumSet.noneOf(ElemType.class), in);
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeByte(ANNOTATION_USAGE);
        myType.save(out);
        RW.save(myUsedArguments, out);
        RW.save(myUsedTargets, elementTypeExternalizer, out);
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }

    @Override
    public int getOwner() {
      return myType.className;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AnnotationUsage that = (AnnotationUsage)o;

      if (myUsedArguments != null ? !myUsedArguments.equals(that.myUsedArguments) : that.myUsedArguments != null) return false;
      if (myUsedTargets != null ? !myUsedTargets.equals(that.myUsedTargets) : that.myUsedTargets != null) return false;
      if (myType != null ? !myType.equals(that.myType) : that.myType != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myType != null ? myType.hashCode() : 0;
      result = 31 * result + (myUsedArguments != null ? myUsedArguments.hashCode() : 0);
      result = 31 * result + (myUsedTargets != null ? myUsedTargets.hashCode() : 0);
      return result;
    }

    @Override
    public void toStream(final DependencyContext context, final PrintStream stream) {
      stream.println("    AnnotationUsage:");
      stream.println("      Type     : " + myType.getDescr(context));

      final List<String> arguments = new LinkedList<>();

      if (myUsedArguments != null) {
        myUsedArguments.forEach(value -> {
          arguments.add(context.getValue(value));
        });
      }

      Collections.sort(arguments);

      final List<String> targets = new LinkedList<>();

      if (myUsedTargets != null) {
        for (final ElemType e : myUsedTargets) {
          targets.add(e.toString());
        }
      }

      Collections.sort(targets);

      stream.println("      Arguments:");

      for (final String s : arguments) {
        stream.println("        " + s);
      }

      stream.println("      Targets  :");

      for (final String s : targets) {
        stream.println("        " + s);
      }
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

  public static Usage createMetaMethodUsage(final DependencyContext context, final int name, final int owner) {
    return context.getUsage(new MetaMethodUsage(name, owner));
  }

  public static Usage createImportStaticMemberUsage(final DependencyContext context, final int name, final int owner) {
    return context.getUsage(new ImportStaticMemberUsage(name, owner));
  }

  public static Usage createImportStaticOnDemandUsage(final DependencyContext context, final int owner) {
    return context.getUsage(new ImportStaticOnDemandUsage(owner));
  }

  public static Usage createClassUsage(final DependencyContext context, final int name) {
    return context.getUsage(new ClassUsage(name));
  }

  public static Usage createClassAsGenericBoundUsage(final DependencyContext context, final int name) {
    return context.getUsage(new ClassAsGenericBoundUsage(name));
  }

  public static Usage createClassNewUsage(final DependencyContext context, final int name) {
    return context.getUsage(new ClassNewUsage(name));
  }

  public static Usage createAnnotationUsage(DependencyContext context,
                                            TypeRepr.ClassType type,
                                            IntSet usedArguments,
                                            Set<ElemType> targets) {
    return context.getUsage(new AnnotationUsage(type, usedArguments, targets));
  }

  public static Usage createModuleUsage(final DependencyContext context, final int name) {
    return context.getUsage(new ModuleUsage(name));
  }

  public static DataExternalizer<Usage> externalizer(final DependencyContext context) {
    return new DataExternalizer<Usage>() {
      @Override
      public void save(@NotNull final DataOutput out, final Usage value) {
        value.save(out);
      }

      @Override
      public Usage read(@NotNull DataInput in) throws IOException {
        final byte tag = in.readByte();
        switch (tag) {
          case CLASS_USAGE:
            return context.getUsage(new ClassUsage(in));

          case CLASS_AS_GENERIC_BOUND_USAGE:
            return context.getUsage(new ClassAsGenericBoundUsage(in));

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

          case MODULE_USAGE:
            return context.getUsage(new ModuleUsage(in));

          case IMPORT_STATIC_MEMBER_USAGE:
            return context.getUsage(new ImportStaticMemberUsage(in));

          case IMPORT_STATIC_ON_DEMAND_USAGE:
            return context.getUsage(new ImportStaticOnDemandUsage(in));

          default:
            throw new IOException("Unknown usage with tag " + tag);
        }
      }
    };
  }
}
