// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.impl.PsiAnnotationStubImpl;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.AnnotationVisitor;
import org.jetbrains.org.objectweb.asm.TypePath;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An immutable container that holds all the type annotations for some type (including internal type components).
 */
public class TypeAnnotationContainer {
  /**
   * A container that contains no type annotations. 
   */
  public static final TypeAnnotationContainer EMPTY = new TypeAnnotationContainer(Collections.emptyList());

  private final List<TypeAnnotationEntry> myList;

  private TypeAnnotationContainer(List<TypeAnnotationEntry> entries) {
    myList = entries;
  }

  /**
   * @return type annotation container for array element 
   * (assuming that this type annotation container is used for the array type)
   */
  public @NotNull TypeAnnotationContainer forArrayElement() {
    if (isEmpty()) return this;
    List<TypeAnnotationEntry> list = ContainerUtil.mapNotNull(myList, entry -> entry.forPathElement(TypeAnnotationEntry.ARRAY_ELEMENT));
    return list.isEmpty() ? EMPTY : new TypeAnnotationContainer(list);
  }

  /**
   * @return type annotation container for enclosing class 
   * (assuming that this type annotation container is used for the inner class)
   */
  public @NotNull TypeAnnotationContainer forEnclosingClass() {
    if (isEmpty()) return this;
    List<TypeAnnotationEntry> list = ContainerUtil.mapNotNull(myList, entry -> entry.forPathElement(TypeAnnotationEntry.ENCLOSING_CLASS));
    return list.isEmpty() ? EMPTY : new TypeAnnotationContainer(list);
  }

  /**
   * @return type annotation container for wildcard bound 
   * (assuming that this type annotation container is used for the bounded wildcard type)
   */
  public @NotNull TypeAnnotationContainer forBound() {
    if (isEmpty()) return this;
    List<TypeAnnotationEntry> list = ContainerUtil.mapNotNull(myList, entry -> entry.forPathElement(TypeAnnotationEntry.WILDCARD_BOUND));
    return list.isEmpty() ? EMPTY : new TypeAnnotationContainer(list);
  }

  /**
   * @param index type argument index
   * @return type annotation container for given type argument
   * (assuming that this type annotation container is used for class type with type arguments)
   */
  public @NotNull TypeAnnotationContainer forTypeArgument(int index) {
    if (isEmpty()) return this;
    List<TypeAnnotationEntry> list = ContainerUtil.mapNotNull(myList, e -> e.forTypeArgument(index));
    return list.isEmpty() ? EMPTY : new TypeAnnotationContainer(list);
  }

  /**
   * @return true if this type annotation container contains no type annotations
   */
  public boolean isEmpty() {
    return myList.isEmpty();
  }

  /**
   * @param parent parent element for annotations
   * @return TypeAnnotationProvider that provides all the top-level annotations  
   */
  public TypeAnnotationProvider getProvider(PsiElement parent) {
    if (isEmpty()) return TypeAnnotationProvider.EMPTY;
    return new TypeAnnotationProvider() {
      @Override
      public PsiAnnotation @NotNull [] getAnnotations() {
        List<PsiAnnotation> result = new ArrayList<>();
        for (TypeAnnotationEntry entry : myList) {
          if (entry.myPath.length == 0) {
            Ref<PsiAnnotation> annotationRef = Ref.create();
            PsiAnnotationStubImpl stub = new PsiAnnotationStubImpl(null, entry.myText) {
              @Override
              public Project getProject() {
                return parent.getProject();
              }

              @Override
              public PsiAnnotation getPsi() {
                return annotationRef.get();
              }
            };
            PsiAnnotation annotation = new ClsAnnotationImpl(stub) {
              @Override
              public PsiFile getContainingFile() {
                return parent.getContainingFile();
              }

              @Override
              public PsiElement getParent() {
                return parent;
              }
            };
            annotationRef.set(annotation);
            result.add(annotation);
          }
        }
        return result.toArray(PsiAnnotation.EMPTY_ARRAY);
      }
    };
  }

  /**
   * Serializes TypeAnnotationContainer into the supplied stream.
   * 
   * @param dataStream stream to write to
   * @param container a container to serialize
   * @throws IOException if the stream throws
   */
  public static void writeTypeAnnotations(@NotNull StubOutputStream dataStream, @NotNull TypeAnnotationContainer container)
    throws IOException {
    dataStream.writeShort(container.myList.size());
    for (TypeAnnotationEntry entry : container.myList) {
      dataStream.writeShort(entry.myPath.length);
      dataStream.write(entry.myPath);
      dataStream.writeUTFFast(entry.myText);
    }
  }

  /**
   * Reads TypeAnnotationContainer from the supplied stream.
   * 
   * @param dataStream stream to read from
   * @return deserialized TypeAnnotationContainer
   * @throws IOException if the stream throws
   */
  public static @NotNull TypeAnnotationContainer readTypeAnnotations(@NotNull StubInputStream dataStream) throws IOException {
    short count = dataStream.readShort();
    TypeAnnotationEntry[] entries = new TypeAnnotationEntry[count];
    for (int i = 0; i < count; i++) {
      short pathLength = dataStream.readShort();
      byte[] path = new byte[pathLength];
      dataStream.readFully(path);
      String text = dataStream.readUTFFast();
      entries[i] = new TypeAnnotationEntry(path, text);
    }
    return new TypeAnnotationContainer(Arrays.asList(entries));
  }

  @Override
  public String toString() {
    return StringUtil.join(myList, "\n");
  }

  static class Builder {
    private final @NotNull ArrayList<TypeAnnotationEntry> myList = new ArrayList<>();
    private final @NotNull TypeInfo myTypeInfo;
    private final @NotNull InnerClassInfo myInnerClassInfo;

    Builder(@NotNull TypeInfo info, @NotNull InnerClassInfo classInfo) {
      myTypeInfo = info;
      myInnerClassInfo = classInfo;
    }

    void add(TypePath path, String text) {
      byte[] translated = translatePath(path);
      if (translated != null) {
        myList.add(new TypeAnnotationEntry(translated, text));
      }
    }

    AnnotationVisitor collect(@Nullable TypePath path, @Nullable String desc) {
      return new AnnotationTextCollector(desc, myInnerClassInfo, text -> add(path, text));
    }

    void build() {
      if (myList.isEmpty()) {
        myTypeInfo.setTypeAnnotations(EMPTY);
      }
      else {
        myList.trimToSize();
        myTypeInfo.setTypeAnnotations(new TypeAnnotationContainer(myList));
      }
    }

    /**
     * Translate annotation path. The most non-trivial thing here is converting inner-to-outer traversal
     * into outer-to-inner. E.g. consider {@code @A Outer.@B Inner} (assuming that Inner is non-static). 
     * Class-file stores empty path for {@code @A} and INNER_TYPE path for {@code @A}. We need the reverse,
     * as when we build the PSI we don't know how many non-static components we have. So we translate path
     * for {@code @A} to ENCLOSING_CLASS and path for {@code @B} to empty.
     * 
     * @param path TypePath
     * @return translated path in the form of byte array
     */
    private byte[] translatePath(@Nullable TypePath path) {
      String typeText = myTypeInfo.text;
      int arrayLevel = myTypeInfo.arrayCount + (myTypeInfo.isEllipsis ? 1 : 0);
      String qualifiedName = PsiNameHelper.getQualifiedClassName(typeText, false);
      int depth = myInnerClassInfo.getInnerDepth(qualifiedName);
      boolean atWildcard = false;
      if (path == null) {
        if (depth == 0 || arrayLevel > 0) {
          return ArrayUtil.EMPTY_BYTE_ARRAY;
        }
        byte[] result = new byte[depth];
        Arrays.fill(result, TypeAnnotationEntry.ENCLOSING_CLASS);
        return result;
      }
      ByteArrayOutputStream result = new ByteArrayOutputStream();
      int length = path.getLength();
      for (int i = 0; i < length; i++) {
        byte step = (byte)path.getStep(i);
        switch (step) {
          case TypePath.INNER_TYPE:
            if (depth == 0) return null;
            depth--;
            break;
          case TypePath.ARRAY_ELEMENT:
            if (arrayLevel <= 0 || atWildcard) return null;
            arrayLevel--;
            result.write(TypeAnnotationEntry.ARRAY_ELEMENT);
            break;
          case TypePath.WILDCARD_BOUND:
            if (!atWildcard) return null;
            atWildcard = false;
            result.write(TypeAnnotationEntry.WILDCARD_BOUND);
            break;
          case TypePath.TYPE_ARGUMENT:
            if (atWildcard || arrayLevel > 0) return null;
            while (depth-- > 0) {
              result.write(TypeAnnotationEntry.ENCLOSING_CLASS);
              typeText = PsiNameHelper.getOuterClassReference(typeText);
            }
            int argumentIndex = path.getStepArgument(i);
            String[] arguments = PsiNameHelper.getClassParametersText(typeText);
            if (argumentIndex >= arguments.length) return null;
            TypeInfo argument = TypeInfo.fromString(arguments[argumentIndex], false);
            arrayLevel = argument.arrayCount;
            typeText = argument.text;
            if (typeText.startsWith("? extends ")) {
              typeText = typeText.substring("? extends ".length());
              atWildcard = true;
            }
            else if (typeText.startsWith("? super ")) {
              typeText = typeText.substring("? super ".length());
              atWildcard = true;
            }
            qualifiedName = PsiNameHelper.getQualifiedClassName(typeText, false);
            depth = myInnerClassInfo.getInnerDepth(qualifiedName);
            result.write(TypeAnnotationEntry.TYPE_ARGUMENT);
            result.write(argumentIndex);
            break;
        }
      }
      if (!atWildcard && arrayLevel == 0) {
        while (depth-- > 0) {
          result.write(TypeAnnotationEntry.ENCLOSING_CLASS);
        }
      }
      return result.toByteArray();
    }
  }

  private static class TypeAnnotationEntry {
    private static final byte ARRAY_ELEMENT = 0;
    private static final byte ENCLOSING_CLASS = 1;
    private static final byte WILDCARD_BOUND = 2;
    private static final byte TYPE_ARGUMENT = 3;
    /**
     * path is stored as the sequence of ARRAY_ELEMENT, ENCLOSING_CLASS, WILDCARD_BOUND and TYPE_ARGUMENT bytes.
     * The TYPE_ARGUMENT byte is followed by the type argument index byte.
     */
    final byte @NotNull [] myPath;
    final @NotNull String myText;

    private TypeAnnotationEntry(byte @NotNull [] path, @NotNull String text) {
      myPath = path;
      myText = text;
    }

    private TypeAnnotationEntry forPathElement(int wanted) {
      if (myPath.length > 0 && myPath[0] == wanted) {
        return new TypeAnnotationEntry(Arrays.copyOfRange(myPath, 1, myPath.length), myText);
      }
      return null;
    }

    public TypeAnnotationEntry forTypeArgument(int index) {
      if (myPath.length > 1 && myPath[0] == TYPE_ARGUMENT && myPath[1] == index) {
        return new TypeAnnotationEntry(Arrays.copyOfRange(myPath, 2, myPath.length), myText);
      }
      return null;
    }

    @Override
    public String toString() {
      StringBuilder result = new StringBuilder();
      int pos = 0;
      while (pos < myPath.length) {
        switch (myPath[pos]) {
          case ARRAY_ELEMENT:
            result.append('[');
            break;
          case ENCLOSING_CLASS:
            result.append('.');
            break;
          case WILDCARD_BOUND:
            result.append('*');
            break;
          case TYPE_ARGUMENT:
            result.append(myPath[++pos]).append(';');
            break;
        }
        pos++;
      }
      return result + "->" + myText;
    }
  }
}
