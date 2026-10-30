// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.openapi.util.Key;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The position of a type annotation, which does not depend on the PSI instance of the annotation.
 * <p>
 * A stub-based declaration creates its types from text, so the annotations inside these types are detached copies.
 * When the AST loads, the declaration gives new annotation instances at the same positions.
 * Both instances give equal paths.
 * <p>
 * The path is the sequence of {@link #ARRAY_ELEMENT}, {@link #ENCLOSING_CLASS}, {@link #WILDCARD_BOUND}
 * and {@link #TYPE_ARGUMENT} bytes. The {@link #TYPE_ARGUMENT} byte is followed by the type argument index byte.
 */
@ApiStatus.Internal
public final class TypeAnnotationPath {
  public static final byte ARRAY_ELEMENT = 0;
  public static final byte ENCLOSING_CLASS = 1;
  public static final byte WILDCARD_BOUND = 2;
  public static final byte TYPE_ARGUMENT = 3;

  private static final Key<Anchor> STUB_ANCHOR = Key.create("TypeAnnotationPath.STUB_ANCHOR");
  private static final TokenSet ARRAY_TOKENS = TokenSet.create(JavaTokenType.LBRACKET, JavaTokenType.ELLIPSIS);

  private final @NotNull PsiElement myAnchor;
  private final int myListIndex;
  private final byte @NotNull [] myPath;
  private final int myIndex;

  private TypeAnnotationPath(@NotNull Anchor anchor, byte @NotNull [] path, int index) {
    myAnchor = anchor.myElement;
    myListIndex = anchor.myListIndex;
    myPath = path;
    myIndex = index;
  }

  /**
   * Marks a type element that a stub-based declaration creates from text.
   *
   * @param typeElement detached type element of the declaration type
   * @param owner       declaration that owns the type
   */
  public static void markStubType(@NotNull PsiTypeElement typeElement, @NotNull PsiModifierListOwner owner) {
    typeElement.putUserData(STUB_ANCHOR, new Anchor(owner, -1));
  }

  /**
   * Marks a reference that a stub-based reference list creates from text.
   *
   * @param reference detached reference
   * @param list      reference list that owns the reference
   * @param index     index of the reference in the list
   */
  public static void markStubReference(@NotNull PsiJavaCodeReferenceElement reference, @NotNull PsiReferenceList list, int index) {
    reference.putUserData(STUB_ANCHOR, new Anchor(list, index));
  }

  /**
   * @param annotation type annotation
   * @return the path of the annotation; null if the annotation is not inside the type of a declaration or a reference list,
   * or if the annotation is in a modifier list
   */
  public static @Nullable TypeAnnotationPath of(@NotNull PsiAnnotation annotation) {
    if (annotation instanceof PsiCompiledElement) return null;
    // Path steps from the innermost to the outermost
    List<byte[]> steps = new ArrayList<>();
    PsiElement element = annotation;
    PsiElement parent = annotation.getParent();
    while (true) {
      if (parent instanceof PsiTypeElement) {
        if (element instanceof PsiAnnotation) {
          addArrayElements(steps, countArrayTokens(parent.getFirstChild(), element));
        }
        else if (element instanceof PsiTypeElement) {
          if (isWildcard(parent)) {
            steps.add(new byte[]{WILDCARD_BOUND});
          }
          else {
            addArrayElements(steps, countArrayTokens(parent.getFirstChild(), null));
          }
        }
        else if (!(element instanceof PsiJavaCodeReferenceElement)) {
          return null;
        }
      }
      else if (parent instanceof PsiReferenceParameterList) {
        int index = ArrayUtil.indexOf(((PsiReferenceParameterList)parent).getTypeParameterElements(), element);
        if (index < 0 || index > Byte.MAX_VALUE) return null;
        steps.add(new byte[]{TYPE_ARGUMENT, (byte)index});
      }
      else if (parent instanceof PsiJavaCodeReferenceElement) {
        if (element instanceof PsiJavaCodeReferenceElement) {
          steps.add(new byte[]{ENCLOSING_CLASS});
        }
        else if (!(element instanceof PsiAnnotation) && !(element instanceof PsiReferenceParameterList)) {
          return null;
        }
      }
      else {
        break;
      }
      element = parent;
      parent = parent.getParent();
    }
    Anchor anchor = element.getUserData(STUB_ANCHOR);
    if (anchor == null) {
      anchor = findAstAnchor(element, steps);
      if (anchor == null) return null;
    }
    ByteArrayOutputStream path = new ByteArrayOutputStream();
    for (int i = steps.size() - 1; i >= 0; i--) {
      byte[] step = steps.get(i);
      path.write(step, 0, step.length);
    }
    return new TypeAnnotationPath(anchor, path.toByteArray(), indexInParent(annotation));
  }

  private static @Nullable Anchor findAstAnchor(@NotNull PsiElement root, @NotNull List<byte[]> steps) {
    PsiElement parent = root.getParent();
    if (root instanceof PsiTypeElement &&
        (parent instanceof PsiMethod && ((PsiMethod)parent).getReturnTypeElement() == root ||
         parent instanceof PsiVariable && ((PsiVariable)parent).getTypeElement() == root)) {
      // C-style array brackets after the name or the parameter list
      addArrayElements(steps, countArrayTokens(root.getNextSibling(), null));
      return new Anchor(parent, -1);
    }
    if (root instanceof PsiJavaCodeReferenceElement && parent instanceof PsiReferenceList) {
      int index = ArrayUtil.indexOf(((PsiReferenceList)parent).getReferenceElements(), root);
      if (index >= 0) return new Anchor(parent, index);
    }
    return null;
  }

  private static boolean isWildcard(@NotNull PsiElement typeElement) {
    for (PsiElement child = typeElement.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (PsiUtil.isJavaToken(child, JavaTokenType.EXTENDS_KEYWORD) || PsiUtil.isJavaToken(child, JavaTokenType.SUPER_KEYWORD)) {
        return true;
      }
    }
    return false;
  }

  private static int countArrayTokens(@Nullable PsiElement from, @Nullable PsiElement to) {
    int count = 0;
    for (PsiElement child = from; child != null && child != to; child = child.getNextSibling()) {
      if (PsiUtil.isJavaToken(child, ARRAY_TOKENS)) count++;
    }
    return count;
  }

  private static void addArrayElements(@NotNull List<byte[]> steps, int count) {
    for (int i = 0; i < count; i++) {
      steps.add(new byte[]{ARRAY_ELEMENT});
    }
  }

  private static int indexInParent(@NotNull PsiAnnotation annotation) {
    int index = 0;
    for (PsiElement sibling = annotation.getPrevSibling(); sibling != null; sibling = sibling.getPrevSibling()) {
      if (sibling instanceof PsiAnnotation) index++;
    }
    return index;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    TypeAnnotationPath that = (TypeAnnotationPath)o;
    return myListIndex == that.myListIndex && myIndex == that.myIndex && myAnchor.equals(that.myAnchor) &&
           Arrays.equals(myPath, that.myPath);
  }

  @Override
  public int hashCode() {
    return ((myAnchor.hashCode() * 31 + myListIndex) * 31 + Arrays.hashCode(myPath)) * 31 + myIndex;
  }

  @Override
  public String toString() {
    return myAnchor + (myListIndex >= 0 ? "[" + myListIndex + "]" : "") + Arrays.toString(myPath) + "#" + myIndex;
  }

  private static final class Anchor {
    private final @NotNull PsiElement myElement;
    private final int myListIndex;

    private Anchor(@NotNull PsiElement element, int listIndex) {
      myElement = element;
      myListIndex = listIndex;
    }
  }
}
