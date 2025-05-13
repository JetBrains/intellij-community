// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import com.intellij.util.ArrayUtilRt;
import kotlin.metadata.*;
import kotlin.metadata.jvm.JvmExtensionsKt;
import kotlin.metadata.jvm.KotlinClassHeader;
import kotlin.metadata.jvm.KotlinClassMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.dependency.impl.RW;
import org.jetbrains.jps.javac.Iterators;

import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

/**
 * A set of data needed to create a kotlin.Metadata annotation instance parsed from bytecode.
 * The created annotation instance can be further introspected with <a href="https://github.com/JetBrains/kotlin/tree/master/libraries/kotlinx-metadata/jvm">kotlinx-metadata-jvm</a> library
 */
public final class KotlinMeta implements JvmMetadata<KotlinMeta, KotlinMeta.Diff> {
  private static final String[] EMPTY_STRING_ARRAY = ArrayUtilRt.EMPTY_STRING_ARRAY;
  private static final int[] EMPTY_INT_ARRAY = new int[0];

  private final int myKind;
  private final int @NotNull [] myVersion;
  private final String @NotNull [] myData1;
  private final String @NotNull [] myData2;
  private final @NotNull String myExtraString;
  private final @NotNull String myPackageName;
  private final int myExtraInt;

  public KotlinMeta(int kind, int @Nullable [] version, String @Nullable [] data1,  String @Nullable [] data2, @Nullable String extraString, @Nullable String packageName, int extraInt) {
    myKind = kind;
    myVersion = version != null? version : EMPTY_INT_ARRAY;
    myData1 = data1 != null? data1 : EMPTY_STRING_ARRAY;
    myData2 = data2 != null? data2 : EMPTY_STRING_ARRAY;
    myExtraString = extraString != null? extraString : "";
    myPackageName = packageName != null? packageName : "";
    myExtraInt = extraInt;
  }

  public KotlinMeta(GraphDataInput in) throws IOException {
    myKind = in.readInt();

    int versionsCount = in.readInt();
    myVersion = versionsCount > 0? new int[versionsCount] : EMPTY_INT_ARRAY;
    for (int idx = 0; idx < versionsCount; idx++) {
      myVersion[idx] = in.readInt();
    }

    myData1 = RW.readCollection(in, in::readUTF).toArray(EMPTY_STRING_ARRAY);
    myData2 = RW.readCollection(in, in::readUTF).toArray(EMPTY_STRING_ARRAY);
    myExtraString = in.readUTF();
    myPackageName = in.readUTF();
    myExtraInt = in.readInt();
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    out.writeInt(myKind);

    out.writeInt(myVersion.length);
    for (int elem : myVersion) {
      out.writeInt(elem);
    }

    RW.writeCollection(out, Arrays.asList(myData1), out::writeUTF);
    RW.writeCollection(out, Arrays.asList(myData2), out::writeUTF);
    out.writeUTF(myExtraString);
    out.writeUTF(myPackageName);
    out.writeInt(myExtraInt);
  }

  public int getKind() {
    return myKind;
  }

  public int @NotNull [] getVersion() {
    return myVersion;
  }

  public String @NotNull [] getData1() {
    return myData1;
  }

  public String @NotNull [] getData2() {
    return myData2;
  }

  public @NotNull String getExtraString() {
    return myExtraString;
  }

  public @NotNull String getPackageName() {
    return myPackageName;
  }

  public @NotNull Integer getExtraInt() {
    return myExtraInt;
  }

  @Override
  public boolean isSame(DiffCapable<?, ?> other) {
    return other instanceof KotlinMeta;
  }

  @Override
  public int diffHashCode() {
    return KotlinMeta.class.hashCode();
  }

  @Override
  public Diff difference(KotlinMeta past) {
    return new Diff(past);
  }

  private KotlinClassMetadata[] myCachedMeta;

  public KotlinClassMetadata getClassMetadata() {
    if (myCachedMeta == null) {
      try {
        myCachedMeta = new KotlinClassMetadata[] {KotlinClassMetadata.readLenient(new KotlinClassHeader(
          getKind(), getVersion(), getData1(), getData2(), getExtraString(), getPackageName(), getExtraInt()
        ))};
      }
      catch (Throwable e) {
        myCachedMeta = new KotlinClassMetadata[] {null};
      }
    }
    return myCachedMeta[0];
  }

  public Iterable<KmProperty> getKmProperties() {
    KmDeclarationContainer container = getDeclarationContainer();
    return container != null? container.getProperties() : Collections.emptyList();
  }

  public Iterable<KmFunction> getKmFunctions() {
    KmDeclarationContainer container = getDeclarationContainer();
    return container != null? container.getFunctions() : Collections.emptyList();
  }

  public Iterable<KmTypeAlias> getKmTypeAliases() {
    KmDeclarationContainer container = getDeclarationContainer();
    return container != null? container.getTypeAliases() : Collections.emptyList();
  }

  public Iterable<KmConstructor> getKmConstructors() {
    KmDeclarationContainer container = getDeclarationContainer();
    return container instanceof KmClass? ((KmClass)container).getConstructors() : Collections.emptyList();
  }

  public Iterable<KmTypeParameter> getTypeParameters() {
    KmDeclarationContainer container = getDeclarationContainer();
    return container instanceof KmClass? ((KmClass)container).getTypeParameters() : Collections.emptyList();
  }

  public Iterable<String> getSealedSubclasses() {
    KmDeclarationContainer container = getDeclarationContainer();
    return container instanceof KmClass? ((KmClass)container).getSealedSubclasses() : Collections.emptyList();
  }

  public Visibility getContainerVisibility() {
    KmDeclarationContainer container = getDeclarationContainer();
    return container instanceof KmClass? Attributes.getVisibility((KmClass)container) : Visibility.PUBLIC;
  }

  public @Nullable Modality getContainerModality() {
    KmDeclarationContainer container = getDeclarationContainer();
    return container instanceof KmClass? Attributes.getModality((KmClass)container) : null;
  }

  public boolean isTopLevelDeclarationContainer() {
    return getDeclarationContainer() instanceof KmPackage;
  }

  public @Nullable KmDeclarationContainer getDeclarationContainer() {
    KotlinClassMetadata clsMeta = getClassMetadata();
    if (clsMeta instanceof KotlinClassMetadata.Class) {
      return ((KotlinClassMetadata.Class)clsMeta).getKmClass();
    }
    if (clsMeta instanceof KotlinClassMetadata.FileFacade) {
      return ((KotlinClassMetadata.FileFacade)clsMeta).getKmPackage();
    }
    if (clsMeta instanceof KotlinClassMetadata.MultiFileClassPart) {
      return ((KotlinClassMetadata.MultiFileClassPart)clsMeta).getKmPackage();
    }
    return null;
  }

  private static boolean kmTypeProjectionEquals(KmTypeProjection p1, KmTypeProjection p2) {
    if (p1 == null) {
      return p2 == null;
    }
    return p2 != null && Objects.equals(p1.getVariance(), p2.getVariance()) && kmTypesEqual(p1.getType(), p2.getType());
  }

  private static boolean kmTypesEqual(KmType t1, KmType t2) {
    // todo: can be removed when KmType properly defines equals() and hashCode()
    if (t1 == null) { 
      return t2 == null;
    }
    return t2 != null
      && Attributes.isNullable(t1) == Attributes.isNullable(t2)
      && Objects.equals(t1.getClassifier(), t2.getClassifier())
      && kmTypesEqual(t1.getOuterType(), t2.getOuterType())
      && Iterators.equals(t1.getArguments(), t2.getArguments(), KotlinMeta::kmTypeProjectionEquals);
  }

  public final class Diff implements Difference {

    private final KotlinMeta myPast;
    private final Supplier<Specifier<KmFunction, KmFunctionsDiff>> myFunctionsDiff;
    private final Supplier<Specifier<KmConstructor, KmConstructorsDiff>> myConstructorsDiff;
    private final Supplier<Specifier<KmProperty, KmPropertiesDiff>> myPropertiesDiff;
    private final Supplier<Specifier<KmTypeAlias, KmTypeAliasDiff>> myAliasesDiff;
    private final Supplier<Specifier<String, ?>> mySealedSubclassesDiff;

    Diff(KotlinMeta past) {
      myPast = past;

      myFunctionsDiff = Utils.lazyValue(() -> Difference.deepDiff(
        myPast.getKmFunctions(), getKmFunctions(),
        (f1, f2) -> Objects.equals(JvmExtensionsKt.getSignature(f1),JvmExtensionsKt.getSignature(f2)),
        f -> Objects.hashCode(JvmExtensionsKt.getSignature(f)),
        KmFunctionsDiff::new
      ));

      myConstructorsDiff = Utils.lazyValue(() -> Difference.deepDiff(
        myPast.getKmConstructors(), getKmConstructors(),
        (c1, c2) -> Objects.equals(JvmExtensionsKt.getSignature(c1), JvmExtensionsKt.getSignature(c2)),
        c -> Objects.hashCode(JvmExtensionsKt.getSignature(c)),
        KmConstructorsDiff::new
      ));

      myPropertiesDiff = Utils.lazyValue(() -> Difference.deepDiff(
        myPast.getKmProperties(), getKmProperties(),
        (p1, p2) -> Objects.equals(p1.getName(), p2.getName()),
        p -> Objects.hashCode(p.getName()),
        KmPropertiesDiff::new
      ));

      myAliasesDiff = Utils.lazyValue(() -> Difference.deepDiff(
        myPast.getKmTypeAliases(), getKmTypeAliases(),
        (a1, a2) -> Objects.equals(a1.getName(), a2.getName()),
        a -> Objects.hashCode(a.getName()),
        KmTypeAliasDiff::new
      ));

      mySealedSubclassesDiff = Utils.lazyValue(() -> Difference.diff(myPast.getSealedSubclasses(), getSealedSubclasses()));
    }

    @Override
    public boolean unchanged() {
      return
        !kindChanged() && !versionChanged() && !packageChanged() && !extraChanged() && !typeParametersVarianceChanged() && !containerVisibilityChanged() && !containerModalityChanged() && 
        sealedSubclasses().unchanged() && functions().unchanged() && properties().unchanged() && constructors().unchanged() && typeAliases().unchanged()/*&& !dataChanged()*/;
    }

    public boolean kindChanged() {
      return myPast.myKind != myKind;
    }

    public boolean versionChanged() {
      return !Arrays.equals(myPast.myVersion, myVersion);
    }

    //public boolean dataChanged() {
    //  return !Arrays.equals(myPast.myData1, myData1) || !Arrays.equals(myPast.myData2, myData2);
    //}

    public boolean packageChanged() {
      return !Objects.equals(myPast.myPackageName, myPackageName);
    }

    public boolean containerModalityChanged() {
      return myPast.getContainerModality() != getContainerModality();
    }

    public boolean containerVisibilityChanged() {
      return myPast.getContainerVisibility() != getContainerVisibility();
    }

    public boolean containerAccessRestricted() {
      return accessRestricted(getContainerVisibility(), myPast.getContainerVisibility());
    }

    public boolean extraChanged() {
      return myPast.myExtraInt != myExtraInt || !Objects.equals(myPast.myExtraString, myExtraString);
    }

    public boolean typeParametersVarianceChanged() {
      return !Iterators.equals(myPast.getTypeParameters(), getTypeParameters(), (p1, p2) -> p1.getVariance() == p2.getVariance());
    }

    public Specifier<String, ?> sealedSubclasses() {
      return mySealedSubclassesDiff.get();
    }

    public Specifier<KmFunction, KmFunctionsDiff> functions() {
      return myFunctionsDiff.get();
    }

    public Specifier<KmConstructor, KmConstructorsDiff> constructors() {
      return myConstructorsDiff.get();
    }

    public Specifier<KmProperty, KmPropertiesDiff> properties() {
      return myPropertiesDiff.get();
    }

    public Specifier<KmTypeAlias, KmTypeAliasDiff> typeAliases() {
      return myAliasesDiff.get();
    }
  }

  public static final class KmFunctionsDiff implements Difference {
    private final KmFunction past;
    private final KmFunction now;

    public KmFunctionsDiff(KmFunction past, KmFunction now) {
      this.past = past;
      this.now = now;
    }

    @Override
    public boolean unchanged() {
      return !becameNullable() && !argsBecameNotNull() && !visibilityChanged() && !receiverParameterChanged() && !hasDefaultDeclarationChanges() && !parameterArgumentsChanged();
    }

    public boolean becameNullable() {
      return !Attributes.isNullable(past.getReturnType()) && Attributes.isNullable(now.getReturnType());
    }

    public boolean visibilityChanged() {
      return Attributes.getVisibility(past) != Attributes.getVisibility(now);
    }

    public boolean accessRestricted() {
      return KotlinMeta.accessRestricted(Attributes.getVisibility(now), Attributes.getVisibility(past));
    }

    public boolean argsBecameNotNull() {
      var nowIt = getParameterTypes(now).iterator();
      for (KmType pastParam : getParameterTypes(past)) {
        if (!nowIt.hasNext()) {
          // should not happen normally if getParameterTypes correctly collects all KmFunction properties that make up a jvm signature.
          // This check make code resistant to possible future changes in kotlinc
          break;
        }
        KmType nowParam = nowIt.next();
        if (Attributes.isNullable(pastParam) && !Attributes.isNullable(nowParam)) {
          return true;
        }
      }
      return false;
    }

    public boolean receiverParameterChanged() {
      // for example 'fun foo(param: Bar): Any'  => 'fun Bar.foo(): Any'
      // both declarations will have the same JvmSignature in bytecode so functions will be considered the same by this criterion
      return !kmTypesEqual(past.getReceiverParameterType(), now.getReceiverParameterType());
    }

    public boolean hasDefaultDeclarationChanges() {
      int before = Iterators.count(Iterators.filter(past.getValueParameters(), Attributes::getDeclaresDefaultValue));
      int after = Iterators.count(Iterators.filter(now.getValueParameters(), Attributes::getDeclaresDefaultValue));
      if (before == 0) {
        return after > 0; // there were no default declarations, but some parameters now define default values
      }
      return after < before; // default definitions may still exist, but some parameters do not define default values anymore
    }

    public boolean parameterArgumentsChanged() {
      return !Iterators.equals(getParameterTypes(past), getParameterTypes(now), (pastType, nowType) -> Iterators.equals(pastType.getArguments(), nowType.getArguments(), KotlinMeta::kmTypeProjectionEquals));
    }

    private static Iterable<KmType> getParameterTypes(KmFunction f) {
      // return all types that can make up a jvm signature
      return Iterators.filter(Iterators.flat(List.of(f.getContextReceiverTypes(), Iterators.asIterable(f.getReceiverParameterType()), Iterators.map(f.getValueParameters(), KmValueParameter::getType))), Objects::nonNull);
    }
  }
  
  public static final class KmTypeAliasDiff implements Difference {
    private final KmTypeAlias past;
    private final KmTypeAlias now;

    public KmTypeAliasDiff(KmTypeAlias past, KmTypeAlias now) {
      this.past = past;
      this.now = now;
    }

    @Override
    public boolean unchanged() {
      return !visibilityChanged() && !underlyingTypeChanged();
    }


    public boolean visibilityChanged() {
      return Attributes.getVisibility(past) != Attributes.getVisibility(now);
    }

    public boolean accessRestricted() {
      return KotlinMeta.accessRestricted(Attributes.getVisibility(now), Attributes.getVisibility(past));
    }

    public boolean underlyingTypeChanged() {
      return !kmTypesEqual(past.getUnderlyingType(), now.getUnderlyingType());
    }
  }

  public static final class KmConstructorsDiff implements Difference {
    private final KmConstructor past;
    private final KmConstructor now;

    public KmConstructorsDiff(KmConstructor past, KmConstructor now) {
      this.past = past;
      this.now = now;
    }

    @Override
    public boolean unchanged() {
      return !argsBecameNotNull() && !visibilityChanged() && !hasDefaultDeclarationChanges();
    }

    public boolean visibilityChanged() {
      return Attributes.getVisibility(past) != Attributes.getVisibility(now);
    }

    public boolean accessRestricted() {
      return KotlinMeta.accessRestricted(Attributes.getVisibility(now), Attributes.getVisibility(past));
    }

    public boolean argsBecameNotNull() {
      var nowIt = getParameterTypes(now).iterator();
      for (KmType pastParam : getParameterTypes(past)) {
        if (!nowIt.hasNext()) {
          // should not happen normally if getParameterTypes correctly collects all KmFunction properties that make up a jvm signature.
          // This check make code resistant to possible future changes in kotlinc
          break;
        }
        KmType nowParam = nowIt.next();
        if (Attributes.isNullable(pastParam) && !Attributes.isNullable(nowParam)) {
          return true;
        }
      }
      return false;
    }

    public boolean hasDefaultDeclarationChanges() {
      int before = Iterators.count(Iterators.filter(past.getValueParameters(), Attributes::getDeclaresDefaultValue));
      int after = Iterators.count(Iterators.filter(now.getValueParameters(), Attributes::getDeclaresDefaultValue));
      if (before == 0) {
        return after > 0; // there were no default declarations, but some parameters now define default values
      }
      return after < before; // default definitions still exist, but some parameters do not define default values anymore
    }

    private static Iterable<KmType> getParameterTypes(KmConstructor f) {
      return Iterators.map(f.getValueParameters(), KmValueParameter::getType);
    }
  }

  public static final class KmPropertiesDiff implements Difference {
    private final KmProperty past;
    private final KmProperty now;

    public KmPropertiesDiff(KmProperty past, KmProperty now) {
      this.past = past;
      this.now = now;
    }

    @Override
    public boolean unchanged() {
      return !typeChanged() && !visibilityChanged() && !customAccessorAdded();
    }

    public boolean typeChanged() {
      return !kmTypesEqual(past.getReturnType(), now.getReturnType());
    }

    public boolean becameNullable() {
      return !Attributes.isNullable(past.getReturnType()) && Attributes.isNullable(now.getReturnType());
    }

    public boolean becameNotNull() {
      return Attributes.isNullable(past.getReturnType()) && !Attributes.isNullable(now.getReturnType());
    }

    public boolean visibilityChanged() {
      return Attributes.getVisibility(past) != Attributes.getVisibility(now) || getGetterVisibility(past) != getGetterVisibility(now) || getSetterVisibility(past) != getSetterVisibility(now);
    }

    public boolean accessRestricted() {
      return KotlinMeta.accessRestricted(Attributes.getVisibility(now), Attributes.getVisibility(past));
    }

    public boolean getterAccessRestricted() {
      return KotlinMeta.accessRestricted(getGetterVisibility(now), getGetterVisibility(past));
    }

    public boolean setterAccessRestricted() {
      return KotlinMeta.accessRestricted(getSetterVisibility(now), getSetterVisibility(past));
    }

    public boolean customAccessorAdded() {
      return (!hasCustomGetter(past) && hasCustomGetter(now)) || (!hasCustomSetter(past) && hasCustomSetter(now));
    }

    private static Visibility getGetterVisibility(KmProperty prop) {
      return Attributes.getVisibility(prop.getGetter());
    }

    private static Visibility getSetterVisibility(KmProperty prop) {
      KmPropertyAccessorAttributes setter = prop.getSetter();
      return setter != null? Attributes.getVisibility(setter) : Visibility.PUBLIC /*use default visibility if setter is not defined*/;
    }

    private static boolean hasCustomGetter(KmProperty prop) {
      return Attributes.isNotDefault(prop.getGetter());
    }

    private static boolean hasCustomSetter(KmProperty prop) {
      KmPropertyAccessorAttributes setter = prop.getSetter();
      return setter != null && Attributes.isNotDefault(setter);
    }
  }

  public static boolean accessRestricted(Visibility now, Visibility past) {
    if (getVisibilityLevel(now) < getVisibilityLevel(past)) {
      return true;
    }
    // special case for incomparable visibility levels: the other way around can be seen as access restriction too
    return past == Visibility.PROTECTED && now == Visibility.INTERNAL;
  }

  private static final Map<Visibility, Integer> VISIBILITY_LEVEL = Map.of(
    Visibility.LOCAL, 1,
    Visibility.PRIVATE_TO_THIS, 2,
    Visibility.PRIVATE, 3,
    Visibility.PROTECTED, 4,
    Visibility.INTERNAL, 5,
    Visibility.PUBLIC, 6
  );

  private static int getVisibilityLevel(Visibility v) {
    Integer level = VISIBILITY_LEVEL.get(v);
    return level != null? level : 0;
  }
}
