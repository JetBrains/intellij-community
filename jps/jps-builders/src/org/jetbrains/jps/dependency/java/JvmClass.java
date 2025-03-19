// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.dependency.impl.RW;
import org.jetbrains.jps.javac.Iterators;

import java.io.IOException;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public final class JvmClass extends JVMClassNode<JvmClass, JvmClass.Diff> {
  public static final String OBJECT_CLASS_NAME = "java/lang/Object";

  private final String myOuterFqName;
  private final String mySuperFqName;
  private final Iterable<String> myInterfaces;
  private final Iterable<JvmField> myFields;
  private final Iterable<JvmMethod> myMethods;
  private final Iterable<ElemType> myAnnotationTargets;
  private final @Nullable RetentionPolicy myRetentionPolicy;

  public JvmClass(
    JVMFlags flags, String signature, String fqName, String outFilePath,
    String superFqName,
    String outerFqName,
    Iterable<String> interfaces,
    Iterable<JvmField> fields,
    Iterable<JvmMethod> methods,
    Iterable<ElementAnnotation> annotations,
    Iterable<ElemType> annotationTargets, @Nullable RetentionPolicy retentionPolicy, @NotNull Iterable<Usage> usages, @NotNull Iterable<JvmMetadata<?, ?>> metadata
  ) {
    
    super(flags, signature, fqName, outFilePath, annotations, usages, metadata);
    mySuperFqName = superFqName == null || OBJECT_CLASS_NAME.equals(superFqName)? "" : superFqName;
    myOuterFqName = outerFqName == null? "" : outerFqName;
    myInterfaces = interfaces;
    myFields = fields;
    myMethods = methods;
    myAnnotationTargets = annotationTargets;
    myRetentionPolicy = retentionPolicy;
  }

  public JvmClass(GraphDataInput in) throws IOException {
    super(in);
    myOuterFqName = in.readUTF();
    mySuperFqName = in.readUTF();
    myInterfaces = RW.readCollection(in, () -> in.readUTF());
    myFields = RW.readCollection(in, () -> new JvmField(in));
    myMethods = RW.readCollection(in, () -> new JvmMethod(in));
    myAnnotationTargets = RW.readCollection(in, () -> ElemType.fromOrdinal(in.readInt()));

    RetentionPolicy policy = null;
    int policyOrdinal = in.readInt();
    if (policyOrdinal >= 0) {
      for (RetentionPolicy value : Iterators.filter(Iterators.asIterable(RetentionPolicy.values()), v -> v.ordinal() == policyOrdinal)) {
        policy = value;
        break;
      }
    }
    myRetentionPolicy = policy;
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    super.write(out);
    out.writeUTF(myOuterFqName);
    out.writeUTF(mySuperFqName);
    RW.writeCollection(out, myInterfaces, s -> out.writeUTF(s));
    RW.writeCollection(out, myFields, f -> f.write(out));
    RW.writeCollection(out, myMethods, m -> m.write(out));
    RW.writeCollection(out, myAnnotationTargets, t -> out.writeInt(t.ordinal()));
    out.writeInt(myRetentionPolicy == null? -1 : myRetentionPolicy.ordinal());
  }

  //@Override
  //public Iterable<Usage> getUsages() {
  //  return Iterators.unique(Iterators.flat(List.of(
  //    super.getUsages(),
  //    Iterators.flat(Iterators.map(getSuperTypes(), s -> new TypeRepr.ClassType(s).getUsages())),
  //    Iterators.flat(Iterators.map(getFields(), field -> field.getType().getUsages())),
  //    Iterators.flat(Iterators.map(getMethods(), method -> Iterators.flat(List.of(
  //      method.getType().getUsages(),
  //      Iterators.flat(Iterators.map(method.getArgTypes(), t -> t.getUsages())),
  //      Iterators.flat(Iterators.map(method.getExceptions(), t -> t.getUsages()))
  //    ))))
  //  )));
  //}

  public @NotNull String getPackageName() {
    return getPackageName(getName());
  }

  public static @NotNull String getPackageName(@NotNull String jvmClassName) {
    int index = jvmClassName.lastIndexOf('/');
    return index >= 0? jvmClassName.substring(0, index) : "";
  }

  public @NotNull String getShortName() {
    String fqName = getName();
    if (isInnerClass() && fqName.startsWith(myOuterFqName) && fqName.length() > myOuterFqName.length()) {
      return fqName.substring(myOuterFqName.length() + 1); // for inner classes use 'real' class short name as it appears in source code
    }
    int index = fqName.lastIndexOf('/');
    return index >= 0? fqName.substring(index + 1) : fqName;
  }

  public boolean isInterface() {
    return getFlags().isInterface();
  }

  public boolean isAnonymous() {
    return getFlags().isAnonymous();
  }

  public boolean isSealed() {
    return getFlags().isSealed();
  }

  public boolean isLocal() {
    return getFlags().isLocal();
  }

  public String getSuperFqName() {
    return mySuperFqName;
  }

  public String getOuterFqName() {
    return myOuterFqName;
  }

  public boolean isInnerClass() {
    return myOuterFqName != null && !myOuterFqName.isBlank();
  }

  public Iterable<String> getInterfaces() {
    return myInterfaces;
  }

  public Iterable<String> getSuperTypes() {
    return mySuperFqName.isEmpty() || OBJECT_CLASS_NAME.equals(mySuperFqName)? getInterfaces() : Iterators.flat(Iterators.asIterable(mySuperFqName), getInterfaces());
  }

  public Iterable<JvmField> getFields() {
    return myFields;
  }

  public Iterable<JvmMethod> getMethods() {
    return myMethods;
  }

  public Iterable<ElemType> getAnnotationTargets() {
    return myAnnotationTargets;
  }

  public @Nullable RetentionPolicy getRetentionPolicy() {
    return myRetentionPolicy;
  }

  @Override
  public Diff difference(JvmClass past) {
    return new Diff(past);
  }

  public final class Diff extends JVMClassNode<JvmClass, JvmClass.Diff>.Diff {
    private final Supplier<Specifier<String, ?>> myInterfacesDiff;
    private final Supplier<Specifier<JvmMethod, JvmMethod.Diff>> myMethodsDiff;
    private final Supplier<Specifier<JvmField, JvmField.Diff>> myFieldsDiff;
    private final Supplier<Specifier<ElemType, ?>> myAnnotationTargetsDiff;

    public Diff(JvmClass past) {
      super(past);
      myInterfacesDiff = Utils.lazyValue(() -> Difference.diff(myPast.getInterfaces(), getInterfaces()));
      myMethodsDiff = Utils.lazyValue(() -> Difference.deepDiff(myPast.getMethods(), getMethods()));
      myFieldsDiff = Utils.lazyValue(() -> Difference.deepDiff(myPast.getFields(), getFields()));
      myAnnotationTargetsDiff = Utils.lazyValue(() -> Difference.diff(myPast.getAnnotationTargets(), getAnnotationTargets()));
    }

    @Override
    public boolean unchanged() {
      return
        super.unchanged() &&
        !superClassChanged() &&
        !outerClassChanged() &&
        interfaces().unchanged() &&
        methods().unchanged() &&
        fields().unchanged() &&
        !retentionPolicyChanged() &&
        annotationTargets().unchanged();
    }

    public boolean superClassChanged() {
      return !Objects.equals(myPast.getSuperFqName(), getSuperFqName());
    }

    public boolean extendsAdded() {
      String pastSuper = myPast.getSuperFqName();
      return (pastSuper.isEmpty() || OBJECT_CLASS_NAME.equals(pastSuper)) && superClassChanged();
    }

    public boolean extendsRemoved() {
      String currentSuper = getSuperFqName();
      return (currentSuper.isEmpty() || OBJECT_CLASS_NAME.equals(currentSuper)) && superClassChanged();
    }

    public boolean outerClassChanged() {
      return !Objects.equals(myPast.getOuterFqName(), getOuterFqName());
    }

    public Specifier<String, ?> interfaces() {
      return myInterfacesDiff.get();
    }

    public Specifier<JvmMethod, JvmMethod.Diff> methods() {
      return myMethodsDiff.get();
    }

    public Specifier<JvmField, JvmField.Diff> fields() {
      return myFieldsDiff.get();
    }

    public boolean retentionPolicyChanged() {
      return !Objects.equals(myPast.getRetentionPolicy(), getRetentionPolicy());
    }

    public Specifier<ElemType, ?> annotationTargets() {
      return myAnnotationTargetsDiff.get();
    }

    public boolean targetAttributeCategoryMightChange() {
      Specifier<ElemType, ?> targetsDiff = annotationTargets();
      if (!targetsDiff.unchanged()) {
        for (ElemType elemType : Set.of(ElemType.TYPE_USE, ElemType.RECORD_COMPONENT)) {
          if (Iterators.contains(targetsDiff.added(), elemType) || Iterators.contains(targetsDiff.removed(), elemType) || Iterators.contains(myPast.getAnnotationTargets(), elemType) ) {
            return true;
          }
        }
      }
      return false;
    }

  }
}
