// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.compiled.ClsAnnotationParameterListImpl;
import com.intellij.psi.impl.compiled.ClsElementImpl;
import com.intellij.psi.impl.compiled.ClsJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiAnnotationStubImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An immutable container that holds all the type annotations for some type (including internal type components).
 */
public final class ExplicitTypeAnnotationContainer implements TypeAnnotationContainer {

  private final List<TypeAnnotationEntry> myList;

  private ExplicitTypeAnnotationContainer(List<TypeAnnotationEntry> entries) {
    if (entries.isEmpty()) {
      throw new IllegalArgumentException("Empty container: use TypeAnnotationContainer.EMPTY instead");
    }
    myList = entries;
  }

  /**
   * @return type annotation container for array element
   * (assuming that this type annotation container is used for the array type)
   */
  @Override
  public @NotNull TypeAnnotationContainer forArrayElement() {
    List<TypeAnnotationEntry> list = ContainerUtil.mapNotNull(myList, entry -> entry.forPathElement(Collector.ARRAY_ELEMENT));
    return list.isEmpty() ? EMPTY : new ExplicitTypeAnnotationContainer(list);
  }

  /**
   * @return type annotation container for enclosing class
   * (assuming that this type annotation container is used for the inner class)
   */
  @Override
  public @NotNull TypeAnnotationContainer forEnclosingClass() {
    List<TypeAnnotationEntry> list = ContainerUtil.mapNotNull(myList, entry -> entry.forPathElement(Collector.ENCLOSING_CLASS));
    return list.isEmpty() ? EMPTY : new ExplicitTypeAnnotationContainer(list);
  }

  /**
   * @return type annotation container for wildcard bound
   * (assuming that this type annotation container is used for the bounded wildcard type)
   */
  @Override
  public @NotNull TypeAnnotationContainer forBound() {
    List<TypeAnnotationEntry> list = ContainerUtil.mapNotNull(myList, entry -> entry.forPathElement(Collector.WILDCARD_BOUND));
    return list.isEmpty() ? EMPTY : new ExplicitTypeAnnotationContainer(list);
  }

  /**
   * @param index type argument index
   * @return type annotation container for given type argument
   * (assuming that this type annotation container is used for class type with type arguments)
   */
  @Override
  public @NotNull TypeAnnotationContainer forTypeArgument(int index) {
    List<TypeAnnotationEntry> list = ContainerUtil.mapNotNull(myList, e -> e.forTypeArgument(index));
    return list.isEmpty() ? EMPTY : new ExplicitTypeAnnotationContainer(list);
  }

  /**
   * @param parent parent element for annotations
   * @return TypeAnnotationProvider that provides all the top-level annotations
   */
  @Override
  public @NotNull TypeAnnotationProvider getProvider(PsiElement parent) {
    return new TypeAnnotationContainerProvider(parent, ObjectUtils.tryCast(parent, PsiAnnotationOwner.class));
  }

  /**
   * Creates PsiAnnotationStub elements for top-level annotations in this container
   *
   * @param parent parent stub
   */
  public void createAnnotationStubs(StubElement<?> parent) {
    for (TypeAnnotationEntry entry : myList) {
      if (entry.myPath.length == 0) {
        new PsiAnnotationStubImpl(parent, entry.myText);
      }
    }
  }

  @Override
  public void appendImmediateText(@NotNull StringBuilder sb) {
    for (TypeAnnotationEntry entry : myList) {
      if (entry.myPath.length == 0) {
        sb.append(entry.myText).append(' ');
      }
    }
  }

  /**
   * Serializes TypeAnnotationContainer into the supplied stream.
   *
   * @param dataStream stream to write to
   * @param container a container to serialize
   * @throws IOException if the stream throws
   */
  public static void writeTypeAnnotations(@NotNull StubOutputStream dataStream, @NotNull ExplicitTypeAnnotationContainer container)
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
    if (count == 0) return EMPTY;
    TypeAnnotationEntry[] entries = new TypeAnnotationEntry[count];
    for (int i = 0; i < count; i++) {
      short pathLength = dataStream.readShort();
      byte[] path = new byte[pathLength];
      dataStream.readFully(path);
      String text = dataStream.readUTFFast();
      entries[i] = new TypeAnnotationEntry(path, text);
    }
    return new ExplicitTypeAnnotationContainer(Arrays.asList(entries));
  }

  @Override
  public String toString() {
    return StringUtil.join(myList, "\n");
  }

  private static @NotNull String encodePath(byte @NotNull [] path) {
    StringBuilder result = new StringBuilder();
    int pos = 0;
    while (pos < path.length) {
      switch (path[pos]) {
        case Collector.ARRAY_ELEMENT:
          result.append("/[]");
          break;
        case Collector.ENCLOSING_CLASS:
          result.append("/.");
          break;
        case Collector.WILDCARD_BOUND:
          result.append("/*");
          break;
        case Collector.TYPE_ARGUMENT:
          result.append('/').append(path[++pos] + 1);
          break;
      }
      pos++;
    }
    return result.toString();
  }

  public static class Collector {
    public static final byte ARRAY_ELEMENT = 0;
    public static final byte ENCLOSING_CLASS = 1;
    public static final byte WILDCARD_BOUND = 2;
    public static final byte TYPE_ARGUMENT = 3;

    private final @NotNull ArrayList<TypeAnnotationEntry> myList = new ArrayList<>();
    protected final @NotNull TypeInfo myTypeInfo;

    public Collector(@NotNull TypeInfo info) {
      myTypeInfo = info;
    }

    public void add(byte @NotNull [] path, @NotNull String text) {
      myList.add(new TypeAnnotationEntry(path, text));
    }

    public void install() {
      if (myList.isEmpty()) {
        myTypeInfo.setTypeAnnotations(EMPTY);
      }
      else {
        myList.trimToSize();
        myTypeInfo.setTypeAnnotations(new ExplicitTypeAnnotationContainer(myList));
      }
    }
  }

  private static class TypeAnnotationEntry {
    /**
     * path is stored as the sequence of ARRAY_ELEMENT, ENCLOSING_CLASS, WILDCARD_BOUND and TYPE_ARGUMENT bytes.
     * The TYPE_ARGUMENT byte is followed by the type argument index byte.
     */
    final byte @NotNull [] myPath;
    final @NotNull String myText;

    private TypeAnnotationEntry(byte @NotNull [] path, @NotNull String text) {
      myPath = path.length == 0 ? ArrayUtil.EMPTY_BYTE_ARRAY : path;
      myText = text;
    }

    private TypeAnnotationEntry forPathElement(int wanted) {
      if (myPath.length > 0 && myPath[0] == wanted) {
        return new TypeAnnotationEntry(Arrays.copyOfRange(myPath, 1, myPath.length), myText);
      }
      return null;
    }

    public TypeAnnotationEntry forTypeArgument(int index) {
      if (myPath.length > 1 && myPath[0] == Collector.TYPE_ARGUMENT && myPath[1] == index) {
        return new TypeAnnotationEntry(Arrays.copyOfRange(myPath, 2, myPath.length), myText);
      }
      return null;
    }

    @Override
    public String toString() {
      return encodePath(myPath) + "->" + myText;
    }
  }

  static class ClsTypeAnnotationImpl extends ClsElementImpl implements PsiAnnotation {
    private final NotNullLazyValue<ClsJavaCodeReferenceElementImpl> myReferenceElement;
    private final NotNullLazyValue<ClsAnnotationParameterListImpl> myParameterList;
    private final PsiElement myParent;
    private final @Nullable PsiAnnotationOwner myOwner;
    private final String myText;

    ClsTypeAnnotationImpl(PsiElement parent, @Nullable PsiAnnotationOwner owner, String text) {
      myParent = parent;
      myOwner = owner;
      myText = text;
      myReferenceElement = NotNullLazyValue.atomicLazy(() -> {
        int index = myText.indexOf('(');
        String refText = index > 0 ? myText.substring(1, index) : myText.substring(1);
        return new ClsJavaCodeReferenceElementImpl(this, refText);
      });
      myParameterList = NotNullLazyValue.atomicLazy(() -> {
        PsiNameValuePair[] attrs = myText.indexOf('(') > 0
                                   ? JavaPsiFacade.getElementFactory(getProject()).createAnnotationFromText(myText, myParent)
                                     .getParameterList().getAttributes()
                                   : PsiNameValuePair.EMPTY_ARRAY;
        return new ClsAnnotationParameterListImpl(this, attrs);
      });
    }

    @Override
    public @NotNull PsiAnnotationParameterList getParameterList() {
      return myParameterList.getValue();
    }

    @Override
    public @Nullable String getQualifiedName() {
      return getNameReferenceElement().getCanonicalText();
    }

    @Override
    public @NotNull PsiJavaCodeReferenceElement getNameReferenceElement() {
      return myReferenceElement.getValue();
    }

    @Override
    public @Nullable PsiAnnotationMemberValue findAttributeValue(@Nullable String attributeName) {
      return PsiImplUtil.findAttributeValue(this, attributeName);
    }

    @Override
    public @Nullable PsiAnnotationMemberValue findDeclaredAttributeValue(@Nullable String attributeName) {
      return PsiImplUtil.findDeclaredAttributeValue(this, attributeName);
    }

    @Override
    public <T extends PsiAnnotationMemberValue> T setDeclaredAttributeValue(@Nullable String attributeName, @Nullable T value) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable PsiAnnotationOwner getOwner() {
      return myOwner;
    }

    @Override
    public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
      buffer.append(myText);
    }

    @Override
    public String getText() {
      return myText;
    }

    @Override
    protected void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
      setMirrorCheckingType(element, null);
      PsiAnnotation mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);
      setMirror(getNameReferenceElement(), mirror.getNameReferenceElement());
      setMirror(getParameterList(), mirror.getParameterList());
    }

    @Override
    public PsiElement @NotNull [] getChildren() {
      return new PsiElement[]{myReferenceElement.getValue(), getParameterList()};
    }

    @Override
    public PsiElement getParent() {
      return myParent;
    }

    @Override
    public void accept(@NotNull PsiElementVisitor visitor) {
      if (visitor instanceof JavaElementVisitor) {
        ((JavaElementVisitor)visitor).visitAnnotation(this);
      }
      else {
        visitor.visitElement(this);
      }
    }
  }

  private class TypeAnnotationContainerProvider implements TypeAnnotationProvider {
    private final PsiElement myParent;
    private final @Nullable PsiAnnotationOwner myOwner;

    private TypeAnnotationContainerProvider(PsiElement parent, @Nullable PsiAnnotationOwner owner) { 
      myParent = parent;
      myOwner = owner;
    }

    @Override
    public @NotNull TypeAnnotationProvider withOwner(@NotNull PsiAnnotationOwner owner) {
      return new TypeAnnotationContainerProvider(myParent, owner);
    }

    @Override
    public @NotNull PsiAnnotation @NotNull [] getAnnotations() {
      List<PsiAnnotation> result = new ArrayList<>();
      for (TypeAnnotationEntry entry : myList) {
        if (entry.myPath.length == 0) {
          PsiAnnotation anno = myParent instanceof PsiCompiledElement ? new ClsTypeAnnotationImpl(myParent, myOwner, entry.myText) :
                               JavaPsiFacade.getElementFactory(myParent.getProject()).createAnnotationFromText(entry.myText, myParent);
          result.add(anno);
        }
      }
      return result.toArray(PsiAnnotation.EMPTY_ARRAY);
    }
  }
}
