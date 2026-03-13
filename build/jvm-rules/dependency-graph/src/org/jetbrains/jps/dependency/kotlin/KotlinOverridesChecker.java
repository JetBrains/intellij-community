// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.kotlin;

import kotlin.metadata.Attributes;
import kotlin.metadata.KmFunction;
import kotlin.metadata.KmProperty;
import kotlin.metadata.KmValueParameter;
import kotlin.metadata.Modality;
import kotlin.metadata.Visibility;
import org.jetbrains.jps.dependency.java.JvmClass;
import org.jetbrains.jps.util.Iterators;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.jetbrains.jps.util.Iterators.collect;
import static org.jetbrains.jps.util.Iterators.filter;
import static org.jetbrains.jps.util.Iterators.map;

public interface KotlinOverridesChecker {

  boolean hasOverridableMembers();

  boolean hasOverrideMatchingMembers(JvmClass cls);

  static KotlinOverridesChecker forClass(JvmClass baseClass) {
    boolean isFinal = KJvmUtils.isFinal(baseClass);
    List<KmFunction> overridableFunctions = isFinal? List.of() : collect(filter(KJvmUtils.allKmFunctions(baseClass), KotlinOverridesChecker::isOverridable), new ArrayList<>());
    List<KmProperty> overridableProperties = isFinal? List.of() : collect(filter(KJvmUtils.allKmProperties(baseClass), KotlinOverridesChecker::isOverridable), new ArrayList<>());
    return new KotlinOverridesChecker() {
      @Override
      public boolean hasOverridableMembers() {
        return !overridableFunctions.isEmpty() || !overridableProperties.isEmpty();
      }

      @Override
      public boolean hasOverrideMatchingMembers(JvmClass cls) {
        if (!overridableFunctions.isEmpty()) {
          Iterable<KmFunction> subFunctions = KJvmUtils.allKmFunctions(cls);
          for (KmFunction superFunc : overridableFunctions) {
            for (KmFunction subFunc : subFunctions) {
              if (functionOverrideMatches(superFunc, subFunc)){
                return true;
              }
            }
          }
        }
        if (!overridableProperties.isEmpty()) {
          Iterable<KmProperty> subProps = KJvmUtils.allKmProperties(cls);
          for (KmProperty superProp : overridableProperties) {
            for (KmProperty subProp : subProps) {
              if (propertyOverrideMatches(superProp, subProp)){
                return true;
              }
            }
          }
        }
        return false;
      }
    };
  }

  static boolean isOverridable(KmProperty prop) {
    return !KJvmUtils.isPrivate(prop) && Attributes.getModality(prop) != Modality.FINAL;
  }

  static boolean isOverridable(KmFunction func) {
    return !KJvmUtils.isPrivate(func) && Attributes.getModality(func) != Modality.FINAL;
  }

  /**
   * Checks if subProp can be a valid override of superProp.
   */
  static boolean propertyOverrideMatches(KmProperty superProp, KmProperty subProp) {
    // Name must match
    if (!Objects.equals(superProp.getName(), subProp.getName())) {
      return false;
    }

    // Visibility can only widen
    if (!visibilityCanOverride(Attributes.getVisibility(superProp), Attributes.getVisibility(subProp))) {
      return false;
    }

    // var cannot be overridden by val
    if (Attributes.isVar(superProp) && !Attributes.isVar(subProp)) {
      return false;
    }

    // Receiver type must match
    if (!Objects.equals(superProp.getReceiverParameterType(), subProp.getReceiverParameterType())) {
      return false;
    }

    // Type parameter count must match
    if (superProp.getTypeParameters().size() != subProp.getTypeParameters().size()) {
      return false;
    }

    return true;
  }

  static boolean functionOverrideMatches(KmFunction superFunc, KmFunction subFunc) {
    // Name must match
    if (!Objects.equals(superFunc.getName(), subFunc.getName())) {
      return false;
    }

    // Visibility can only widen
    if (!visibilityCanOverride(Attributes.getVisibility(superFunc), Attributes.getVisibility(subFunc))) {
      return false;
    }

    // Suspend modifier must match
    if (Attributes.isSuspend(superFunc) != Attributes.isSuspend(subFunc)) {
      return false;
    }

    if (!Iterators.equals(map(superFunc.getValueParameters(), KmValueParameter::getType), map(subFunc.getValueParameters(), KmValueParameter::getType))) {
      return false;
    }

    // Receiver type must match
    if (!Objects.equals(superFunc.getReceiverParameterType(), subFunc.getReceiverParameterType())) {
      return false;
    }

    // Type parameter count must match
    if (superFunc.getTypeParameters().size() != subFunc.getTypeParameters().size()) {
      return false;
    }

    return true;
  }

  /**
   * Checks if overridingVisibility is same or wider than baseVisibility in members overriding terms
   */
  static boolean visibilityCanOverride(Visibility baseVis, Visibility overridingVis) {
    if (baseVis == Visibility.PROTECTED) {
      return overridingVis == Visibility.PROTECTED || overridingVis == Visibility.INTERNAL || overridingVis == Visibility.PUBLIC;
    }
    if (baseVis == Visibility.INTERNAL) {
      return overridingVis == Visibility.INTERNAL || overridingVis == Visibility.PUBLIC;
    }
    if (baseVis == Visibility.PUBLIC) {
      return overridingVis == Visibility.PUBLIC;
    }
    return false;
  }

}
