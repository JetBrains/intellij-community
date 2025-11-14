// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ParentAwareTokenSet;
import com.intellij.psi.tree.ParentProviderElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class BasicJavaAstTreeUtil {
  private BasicJavaAstTreeUtil() { }

  @Contract("null ,_ -> false")
  public static boolean is(@Nullable ASTNode element, @Nullable IElementType iElementType) {
    boolean notNull = element != null && iElementType != null;
    if (!notNull) {
      return false;
    }
    IElementType sourceElementType = element.getElementType();
    return is(sourceElementType, iElementType);
  }

  public static boolean is(@NotNull IElementType source, @NotNull IElementType target) {
    if (source == target) {
      return true;
    }
    if (source instanceof ParentProviderElementType) {
      Set<IElementType> parents = ((ParentProviderElementType)source).getParents();
      return ContainerUtil.exists(parents, parent -> parent != null && is(parent, target));
    }
    return false;
  }

  @Contract("null ,_ -> false")
  public static boolean is(@Nullable ASTNode element, @NotNull Set<IElementType> iElementTypes) {
    boolean isNotNull = element != null;
    if (!isNotNull) {
      return false;
    }
    return is(element.getElementType(), iElementTypes);
  }

  //needs for FrontBackJavaTokenSet
  public static boolean is(@Nullable ASTNode element, @NotNull TokenSet tokenSet) {
    boolean isNotNull = element != null;
    if (!isNotNull) {
      return false;
    }
    return ParentProviderElementType.containsWithSourceParent(element.getElementType(), tokenSet);
  }

  public static boolean is(@Nullable ASTNode element, @NotNull ParentAwareTokenSet tokenSet) {
    boolean isNotNull = element != null;
    if (!isNotNull) {
      return false;
    }
    return is(element.getElementType(), tokenSet);
  }

  private static boolean is(@NotNull IElementType source, @NotNull ParentAwareTokenSet tokenSet) {
    //not call iteratively!
    if (tokenSet.contains(source)) {
      return true;
    }
    return false;
  }

  private static boolean is(@NotNull IElementType source, @NotNull Set<IElementType> tokenSet) {
    if (tokenSet.contains(source)) {
      return true;
    }
    if (source instanceof ParentProviderElementType) {
      Set<IElementType> parents = ((ParentProviderElementType)source).getParents();
      return ContainerUtil.exists(parents, parent -> parent != null && is(parent, tokenSet));
    }
    return false;
  }
}