// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.util.Iterators;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.*;
import java.lang.annotation.RetentionPolicy;
import java.util.*;
import java.util.function.Predicate;

@ApiStatus.Internal
public final class ClassRepr extends ClassFileRepr {
  private final TypeRepr.ClassType mySuperClass;
  private final Set<TypeRepr.ClassType> myInterfaces;
  private final Set<ElemType> myAnnotationTargets;
  private final RetentionPolicy myRetentionPolicy;

  private final Set<FieldRepr> myFields;
  private final Set<MethodRepr> myMethods;

  private final int myOuterClassName;
  private final boolean myIsLocal;
  private final boolean myIsAnonymous;
  private final boolean myIsGenerated;
  private boolean myHasInlinedConstants;

  public Set<MethodRepr> getMethods() {
    return myMethods;
  }

  public Set<FieldRepr> getFields() {
    return myFields;
  }

  public int getOuterClassName() {
    return myOuterClassName;
  }

  public boolean isLocal() {
    return myIsLocal;
  }

  public boolean isAnonymous() {
    return myIsAnonymous;
  }

  public boolean isGenerated() {
    return myIsGenerated;
  }

  public boolean hasInlinedConstants() {
    return myHasInlinedConstants;
  }

  public void setHasInlinedConstants(boolean hasConstants) {
    myHasInlinedConstants = hasConstants;
  }

  public TypeRepr.ClassType getSuperClass() {
    return mySuperClass;
  }

  public RetentionPolicy getRetentionPolicy() {
    return myRetentionPolicy;
  }

  public Set<ElemType> getAnnotationTargets() {
    final Set<ElemType> targets = myAnnotationTargets;
    return targets != null ? Collections.unmodifiableSet(targets) : Collections.emptySet();
  }

  public boolean isInterface() {
    return (access & Opcodes.ACC_INTERFACE) != 0;
  }

  public boolean isEnum() {
    return (access & Opcodes.ACC_ENUM) != 0;
  }

  public abstract static class Diff extends DifferenceImpl {

    Diff(@NotNull Difference delegate) {
      super(delegate);
    }

    public abstract Specifier<TypeRepr.ClassType, Difference> interfaces();

    public abstract Specifier<FieldRepr, Difference> fields();

    public abstract Specifier<MethodRepr, MethodRepr.Diff> methods();

    public abstract Specifier<ElemType, Difference> targets();

    public abstract boolean retentionChanged();

    public abstract boolean extendsAdded();

    public abstract boolean targetAttributeCategoryMightChange();

    @Override
    public boolean no() {
      return base() == NONE &&
             interfaces().unchanged() &&
             fields().unchanged() &&
             methods().unchanged() &&
             targets().unchanged() &&
             !retentionChanged();
    }
  }

  @Override
  public Diff difference(final Proto past) {
    final ClassRepr pastClass = (ClassRepr)past;
    final Difference diff = super.difference(past);
    int base = diff.base();

    if (!mySuperClass.equals(pastClass.mySuperClass)) {
      base |= Difference.SUPERCLASS;
    }
    if (!getUsages().equals(pastClass.getUsages())) {
      base |= Difference.USAGES;
    }
    if (hasInlinedConstants() != pastClass.hasInlinedConstants()) {
      base |= Difference.CONSTANT_REFERENCES;
    }

    final int d = base;

    return new Diff(diff) {
      @Override
      public boolean extendsAdded() {
        if ((d & Difference.SUPERCLASS) <= 0) {
          return false;
        }
        final String pastSuperName = myContext.getValue(((ClassRepr)past).mySuperClass.className);
        return "java/lang/Object".equals(pastSuperName);
      }

      @Override
      public Difference.Specifier<TypeRepr.ClassType, Difference> interfaces() {
        return Difference.make(pastClass.myInterfaces, myInterfaces);
      }

      @Override
      public Difference.Specifier<FieldRepr, Difference> fields() {
        return Difference.make(pastClass.myFields, myFields);
      }

      @Override
      public Difference.Specifier<MethodRepr, MethodRepr.Diff> methods() {
        return Difference.make(pastClass.myMethods, myMethods);
      }

      @Override
      public Specifier<ElemType, Difference> targets() {
        return Difference.make(pastClass.myAnnotationTargets, myAnnotationTargets);
      }

      @Override
      public boolean retentionChanged() {
        return !((myRetentionPolicy == null && pastClass.myRetentionPolicy == RetentionPolicy.CLASS) ||
                 (myRetentionPolicy == RetentionPolicy.CLASS && pastClass.myRetentionPolicy == null) ||
                 (myRetentionPolicy == pastClass.myRetentionPolicy));
      }

      @Override
      public boolean targetAttributeCategoryMightChange() {
        final Specifier<ElemType, Difference> targetsDiff = targets();
        if (!targetsDiff.unchanged()) {
          for (ElemType elemType : Set.of(ElemType.TYPE_USE, ElemType.RECORD_COMPONENT)) {
            if (targetsDiff.added().contains(elemType) || targetsDiff.removed().contains(elemType) || pastClass.getAnnotationTargets().contains(elemType) ) {
              return true;
            }
          }
        }
        return false;
      }

      @Override
      public int base() {
        return d;
      }

      @Override
      public boolean hadValue() {
        return false;
      }
    };
  }

  public Iterable<TypeRepr.ClassType> getSuperTypes() {
    return Iterators.flat(Iterators.asIterable(mySuperClass), myInterfaces);
  }

  @Override
  protected void updateClassUsages(final DependencyContext context, final Set<? super UsageRepr.Usage> s) {
    mySuperClass.updateClassUsages(context, name, s);

    for (TypeRepr.AbstractType t : myInterfaces) {
      t.updateClassUsages(context, name, s);
    }

    for (MethodRepr m : myMethods) {
      m.updateClassUsages(context, name, s);
    }

    for (FieldRepr f : myFields) {
      f.updateClassUsages(context, name, s);
    }
  }

  public ClassRepr(final DependencyContext context, final int access, final int fileName, final int name, final int sig,
                   final int superClass,
                   final String[] interfaces,
                   final Set<FieldRepr> fields,
                   final Set<MethodRepr> methods,
                   final Set<TypeRepr.ClassType> annotations,
                   final Set<ElemType> annotationTargets,
                   final RetentionPolicy policy,
                   final int outerClassName,
                   final boolean localClassFlag,
                   final boolean anonymousClassFlag,
                   final Set<UsageRepr.Usage> usages, boolean isGenerated) {
    super(access, sig, name, annotations, fileName, context, usages);
    mySuperClass = TypeRepr.createClassType(context, superClass);
    myInterfaces = TypeRepr.createClassType(context, interfaces, new HashSet<>(1));
    myFields = fields;
    myMethods = methods;
    myAnnotationTargets = annotationTargets;
    myRetentionPolicy = policy;
    myOuterClassName = outerClassName;
    myIsLocal = localClassFlag;
    myIsAnonymous = anonymousClassFlag;
    myIsGenerated = isGenerated;
    updateClassUsages(context, usages);
  }

  public ClassRepr(final DependencyContext context, final DataInput in) {
    super(context, in);
    try {
      mySuperClass = TypeRepr.<TypeRepr.ClassType>externalizer(context).read(in);
      myInterfaces = RW.read(TypeRepr.externalizer(context), new HashSet<>(1), in);
      myFields = RW.read(FieldRepr.externalizer(context), new HashSet<>(), in);
      myMethods = RW.read(MethodRepr.externalizer(context), new HashSet<>(), in);
      myAnnotationTargets = RW.read(UsageRepr.AnnotationUsage.elementTypeExternalizer, EnumSet.noneOf(ElemType.class), in);

      final String s = RW.readUTF(in);

      myRetentionPolicy = s.isEmpty()? null : RetentionPolicy.valueOf(s);

      myOuterClassName = DataInputOutputUtil.readINT(in);
      int flags = DataInputOutputUtil.readINT(in);
      myIsLocal = (flags & LOCAL_MASK) != 0;
      myIsAnonymous = (flags & ANONYMOUS_MASK) != 0;
      myHasInlinedConstants = (flags & HAS_INLINED_CONSTANTS_MASK) != 0;
      myIsGenerated = (flags & IS_GENERATED_MASK) != 0;
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  private static final int LOCAL_MASK = 1;
  private static final int ANONYMOUS_MASK = 2;
  private static final int HAS_INLINED_CONSTANTS_MASK = 4;
  private static final int IS_GENERATED_MASK = 8;

  @Override
  public void save(final DataOutput out) {
    try {
      super.save(out);
      mySuperClass.save(out);
      RW.save(myInterfaces, out);
      RW.save(myFields, out);
      RW.save(myMethods, out);
      RW.save(myAnnotationTargets, UsageRepr.AnnotationUsage.elementTypeExternalizer, out);
      RW.writeUTF(out, myRetentionPolicy == null ? "" : myRetentionPolicy.toString());
      DataInputOutputUtil.writeINT(out, myOuterClassName);
      DataInputOutputUtil.writeINT(
        out, (myIsLocal ? LOCAL_MASK:0) | (myIsAnonymous ? ANONYMOUS_MASK : 0) | (myHasInlinedConstants ? HAS_INLINED_CONSTANTS_MASK : 0) | (myIsGenerated ? IS_GENERATED_MASK : 0)
      );
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  public UsageRepr.Usage createUsage() {
    return UsageRepr.createClassUsage(myContext, name);
  }

  public String getPackageName() {
    final String strValue = myContext.getValue(name);
    return strValue != null? getPackageName(strValue) : null;
  }

  public String getShortName() {
    final String strValue = myContext.getValue(name);
    return strValue != null? getShortName(strValue) : null;
  }

  public static @NotNull String getPackageName(final @NotNull String raw) {
    final int index = raw.lastIndexOf('/');

    if (index == -1) {
      return "";
    }

    return raw.substring(0, index);
  }

  public static @NotNull String getShortName(final @NotNull String fqName) {
    final int index = fqName.lastIndexOf('/');

    if (index == -1) {
      return fqName;
    }

    return fqName.substring(index + 1);
  }

  public @Nullable FieldRepr findField(final int name) {
    for (FieldRepr f : myFields) {
      if (f.name == name) {
        return f;
      }
    }

    return null;
  }

  public @NotNull Collection<MethodRepr> findMethods(final Predicate<? super MethodRepr> p) {
    final Collection<MethodRepr> result = new LinkedList<>();

    for (MethodRepr mm : myMethods) {
      if (p.test(mm)) {
        result.add(mm);
      }
    }

    return result;
  }

  public static DataExternalizer<ClassRepr> externalizer(final DependencyContext context) {
    return new DataExternalizer<>() {
      @Override
      public void save(final @NotNull DataOutput out, final ClassRepr value) {
        value.save(out);
      }

      @Override
      public ClassRepr read(final @NotNull DataInput in) {
        return new ClassRepr(context, in);
      }
    };
  }

  @Override
  public void toStream(final DependencyContext context, final PrintStream stream) {
    super.toStream(context, stream);

    stream.print("      Superclass : ");
    stream.println(mySuperClass.getDescr(context));

    stream.print("      Interfaces : ");
    final TypeRepr.AbstractType[] is = myInterfaces.toArray(TypeRepr.AbstractType.EMPTY_TYPE_ARRAY);
    Arrays.sort(is, Comparator.comparing(o -> o.getDescr(context)));
    for (final TypeRepr.AbstractType t : is) {
      stream.print(t.getDescr(context));
      stream.print(" ");
    }
    stream.println();

    stream.print("      Targets    : ");
    final ElemType[] es = myAnnotationTargets.toArray(new ElemType[0]);
    Arrays.sort(es);
    for (final ElemType e : es) {
      stream.print(e);
      stream.print("; ");
    }
    stream.println();

    stream.print("      Policy     : ");
    stream.println(myRetentionPolicy);

    stream.print("      Outer class: ");
    stream.println(context.getValue(myOuterClassName));

    stream.print("      Local class: ");
    stream.println(myIsLocal);
    stream.print("      Anonymous class: ");
    stream.println(myIsAnonymous);
    stream.print("      Has inlined constants: ");
    stream.println(myHasInlinedConstants);
    stream.print("      IsGenerated: ");
    stream.println(myIsGenerated);

    stream.println("      Fields:");
    final FieldRepr[] fs = myFields.toArray(new FieldRepr[0]);
    Arrays.sort(fs, (o1, o2) -> {
      if (o1.name == o2.name) {
        return o1.myType.getDescr(context).compareTo(o2.myType.getDescr(context));
      }

      return Objects.requireNonNull(context.getValue(o1.name)).compareTo(Objects.requireNonNull(context.getValue(o2.name)));
    });
    for (final FieldRepr f : fs) {
      f.toStream(context, stream);
    }
    stream.println("      End Of Fields");

    stream.println("      Methods:");
    final MethodRepr[] ms = myMethods.toArray(new MethodRepr[0]);
    Arrays.sort(ms, (o1, o2) -> {
      if (o1.name == o2.name) {
        final String d1 = o1.myType.getDescr(context);
        final String d2 = o2.myType.getDescr(context);

        final int c = d1.compareTo(d2);

        if (c == 0) {
          final int l1 = o1.myArgumentTypes.length;
          final int l2 = o2.myArgumentTypes.length;

          if (l1 == l2) {
            for (int i = 0; i<l1; i++) {
              final String d11 = o1.myArgumentTypes[i].getDescr(context);
              final String d22 = o2.myArgumentTypes[i].getDescr(context);

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

      return Objects.requireNonNull(context.getValue(o1.name)).compareTo(Objects.requireNonNull(context.getValue(o2.name)));
    });
    for (final MethodRepr m : ms) {
      m.toStream(context, stream);
    }
    stream.println("      End Of Methods");

    stream.println("      Usages:");

    final List<String> usages = new LinkedList<>();

    for (final UsageRepr.Usage u : getUsages()) {
      final ByteArrayOutputStream bas = new ByteArrayOutputStream();

      u.toStream(myContext, new PrintStream(bas));

      try {
        bas.close();
      }
      catch (final IOException e) {
        throw new BuildDataCorruptedException(e);
      }

      usages.add(bas.toString());
    }

    Collections.sort(usages);

    for (final String s : usages) {
      stream.println(s);
    }

    stream.println("      End Of Usages");
  }
}
