// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.kotlin;

import kotlin.metadata.*;
import kotlin.metadata.jvm.JvmExtensionsKt;
import kotlin.metadata.jvm.JvmMethodSignature;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.ReferenceID;
import org.jetbrains.jps.dependency.java.*;
import org.jetbrains.jps.javac.Iterators;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;

import static org.jetbrains.jps.javac.Iterators.*;

final class KJvmUtils {

  private static final EnumSet<Visibility> PRIVATE_VISIBILITY = EnumSet.of(Visibility.LOCAL, Visibility.PRIVATE_TO_THIS, Visibility.PRIVATE);

  static boolean isSealed(JvmClass cls) {
    return isSealed(getDeclarationContainer(cls));
  }

  static boolean isSealed(KmDeclarationContainer container) {
    return container instanceof KmClass && Attributes.getModality(((KmClass)container)) == Modality.SEALED;
  }

  static Iterable<KmFunction> allKmFunctions(Node<?, ?> node) {
    KotlinMeta meta = getKotlinMeta(node);
    return meta != null? meta.getKmFunctions() : Collections.emptyList();
  }

  static Iterable<KmProperty> allKmProperties(Node<?, ?> node) {
    KotlinMeta meta = getKotlinMeta(node);
    return meta != null? meta.getKmProperties() : Collections.emptyList();
  }

  static @Nullable String getKotlinName(JvmNodeReferenceID cls, Utils utils) {
    return find(map(utils.getNodes(cls, JvmClass.class), c -> getKotlinName(c)), Objects::nonNull);
  }

  static @Nullable String getKotlinName(JvmClass cls) {
    KmDeclarationContainer container = getDeclarationContainer(cls);
    if (container instanceof KmPackage) {
      return cls.getPackageName();
    }
    if (container instanceof KmClass) {
      return ((KmClass)container).getName().replace('.', '/');
    }
    return null;
  }

  static String getMethodKotlinName(JvmClass cls, JvmMethod method) {
    JvmMethodSignature sig = new JvmMethodSignature(method.getName(), method.getDescriptor());
    for (KmFunction f : allKmFunctions(cls)) {
      if (sig.equals(JvmExtensionsKt.getSignature(f))) {
        return f.getName();
      }
    }
    for (KmProperty p : allKmProperties(cls)) {
      JvmMethodSignature getterSig = JvmExtensionsKt.getGetterSignature(p);
      if (sig.equals(getterSig)) {
        return getterSig.getName();
      }
      if (p.getSetter() != null) {
        JvmMethodSignature setterSig = JvmExtensionsKt.getSetterSignature(p);
        if (sig.equals(setterSig)) {
          return setterSig.getName();
        }
      }
    }
    return method.getName();
  }

  static boolean isDeclaresDefaultValue(KmFunction f) {
    return find(f.getValueParameters(), Attributes::getDeclaresDefaultValue) != null;
  }

  static KmDeclarationContainer getDeclarationContainer(Node<?, ?> node) {
    KotlinMeta meta = getKotlinMeta(node);
    return meta != null? meta.getDeclarationContainer() : null;
  }

  static boolean isKotlinNode(Node<?, ?> node) {
    return getKotlinMeta(node) != null;
  }

  static @Nullable KotlinMeta getKotlinMeta(Node<?, ?> node) {
    return node instanceof JVMClassNode? (KotlinMeta)find(((JVMClassNode<?, ?>)node).getMetadata(), mt -> mt instanceof KotlinMeta) : null;
  }

  static boolean isPrivate(KmProperty prop) {
    return isPrivate(Attributes.getVisibility(prop));
  }

  static boolean isPrivate(KmFunction func) {
    return isPrivate(Attributes.getVisibility(func));
  }

  static boolean isPrivate(KmClass cl) {
    return isPrivate(Attributes.getVisibility(cl));
  }

  static boolean isPrivate(Visibility vis) {
    return PRIVATE_VISIBILITY.contains(vis);
  }

  static Iterable<ReferenceID> withAllSubclassesIfSealed(Utils utils, ReferenceID sealedClassId) {
    Iterators.Function<ReferenceID, Iterable<? extends ReferenceID>> withSubclassesIfSealed =
      id -> flat(map(utils.getNodes(id, JvmClass.class), n -> isSealed(n)? utils.directSubclasses(n.getReferenceID()) : Collections.emptyList()));
    return recurse(sealedClassId, withSubclassesIfSealed, true);
  }
}
