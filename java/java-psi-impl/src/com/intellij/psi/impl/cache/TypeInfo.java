// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterASTTokenNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.compiled.SignatureParsing;
import com.intellij.psi.impl.java.stubs.impl.PsiClassStubImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

import static com.intellij.util.BitUtil.clear;
import static com.intellij.util.BitUtil.isSet;

/**
 * Represents a type encoded inside a stub tree
 */
@ApiStatus.Internal
public /*sealed*/ abstract class TypeInfo {
  private static final int HAS_TYPE_ANNOTATIONS = 0x80;

  public static final TypeInfo[] EMPTY_ARRAY = {};

  private static final String[] ourIndexFrequentType;
  private static final Object2IntMap<String> ourFrequentTypeIndex;
  private static final int ourTypeLengthMask;
  static {
    int typeLengthMask = 0;
    ourIndexFrequentType = new String[]{
      "",
      "boolean", "byte", "char", "double", "float", "int", "long", "null", "short", "void",
      CommonClassNames.JAVA_LANG_OBJECT_SHORT, CommonClassNames.JAVA_LANG_OBJECT,
      CommonClassNames.JAVA_LANG_STRING_SHORT, CommonClassNames.JAVA_LANG_STRING
    };

    ourFrequentTypeIndex = new Object2IntOpenHashMap<>();
    for (int i = 0; i < ourIndexFrequentType.length; i++) {
      String type = ourIndexFrequentType[i];
      ourFrequentTypeIndex.put(type, i);
      assert type.length() < 32;
      typeLengthMask |= (1 << type.length());
    }
    assert ourFrequentTypeIndex.size() == ourIndexFrequentType.length;
    ourTypeLengthMask = typeLengthMask;
  }

  private static final TypeKind[] ALL_KINDS = TypeKind.values();
  private static final Map<String, TypeKind> TEXT_TO_KIND = StreamEx.of(ALL_KINDS).mapToEntry(kind -> kind.text, kind -> kind)
    .nonNullKeys().toImmutableMap();

  /**
   * Kind of a type.
   * @implNote Ordinal values are used in the serialization protocol. 
   * Any changes in this enum should be accompanied by serialization version bump.
   */
  public enum TypeKind {
    /**
     * Absent type (e.g., constructor return value)
     */
    NULL,
    // Reference

    /**
     * Simple reference, no outer class, no generic parameters
     */
    REF,
    /**
     * Reference with generic parameters
     */
    GENERIC,

    // References to widely used classes (skip encoding the class name)
    JAVA_LANG_OBJECT(CommonClassNames.JAVA_LANG_OBJECT), JAVA_LANG_STRING(CommonClassNames.JAVA_LANG_STRING),
    JAVA_LANG_THROWABLE(CommonClassNames.JAVA_LANG_THROWABLE), JAVA_LANG_EXCEPTION(CommonClassNames.JAVA_LANG_EXCEPTION),
    JAVA_UTIL_COLLECTION(CommonClassNames.JAVA_UTIL_COLLECTION), JAVA_UTIL_LIST(CommonClassNames.JAVA_UTIL_LIST),
    JAVA_LANG_ITERABLE(CommonClassNames.JAVA_LANG_ITERABLE), JAVA_UTIL_ITERATOR(CommonClassNames.JAVA_UTIL_ITERATOR),
    JAVA_UTIL_MAP(CommonClassNames.JAVA_UTIL_MAP), JAVA_LANG_ANNOTATION_ANNOTATION(CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION),

    /**
     * Reference with outer class (which may probably be inner as well, or have generic parameters), 
     * but current ref has no generic parameters, like {@code Outer<X>.Inner}, or {@code Outer1.Outer2.Inner}
     */
    INNER,
    /**
     * Reference with outer class, which is a simple {@link #REF} itself (avoid encoding inner kind), like {@code Outer.Inner}
     */
    INNER_SIMPLE,
    /**
     * Reference with arbitrary outer class and generic parameters, like {@code Outer.Inner<X>}, or {@code Outer<X>.Inner<Y>}
     */
    INNER_GENERIC,
    // Derived
    /**
     * Wildcard type, like {@code ? extends X}
     */
    EXTENDS,
    /**
     * Wildcard type, like {@code ? super X}
     */
    SUPER,
    /**
     * Array type (component could be primitive, reference, or another array)
     */
    ARRAY,
    /**
     * Ellipsis type (vararg parameter of a method, or a record component)
     */
    ELLIPSIS,
    BOOLEAN("boolean"), BYTE("byte"), CHAR("char"), DOUBLE("double"), FLOAT("float"), INT("int"), LONG("long"), SHORT("short"),
    VOID("void"),
    OBJECT("Object"), STRING("String"), WILDCARD("?");

    private final @Nullable String text;

    TypeKind() {
      this(null);
    }

    TypeKind(@Nullable String text) {
      this.text = text;
    }

    boolean isReference() {
      return ordinal() >= REF.ordinal() && ordinal() <= INNER_GENERIC.ordinal();
    }

    boolean isDerived() {
      return ordinal() >= EXTENDS.ordinal() && ordinal() <= ELLIPSIS.ordinal();
    }
  }

  private final @NotNull TypeKind kind;
  private TypeAnnotationContainer myTypeAnnotations;

  /**
   * Derived type: either array or wildcard
   */
  public static final class DerivedTypeInfo extends TypeInfo {
    private final TypeInfo myChild;

    public DerivedTypeInfo(@NotNull TypeKind kind, @NotNull TypeInfo child) {
      super(kind);
      assert kind.isDerived();
      myChild = child;
    }

    public TypeInfo child() {
      return myChild;
    }

    @Override
    public TypeInfo withEllipsis() {
      switch (getKind()) {
        case ELLIPSIS: return this;
        case ARRAY:
          return new DerivedTypeInfo(TypeKind.ELLIPSIS, myChild);
        default:
          throw new UnsupportedOperationException();
      }
    }

    @Override
    String text(boolean isShort) {
      switch (getKind()) {
        case EXTENDS:
          return "? extends " + myChild.text(isShort);
        case SUPER:
          return "? super " + myChild.text(isShort);
        case ARRAY:
          return myChild.text(isShort) + "[]";
        case ELLIPSIS:
          return myChild.text(isShort) + "...";
        default:
          throw new IllegalStateException();
      }
    }
  }

  /**
   * Reference type; may be inner type or generic type
   */
  public static final class RefTypeInfo extends TypeInfo {
    private final String myName;
    private final @Nullable RefTypeInfo myOuter;
    private final @NotNull TypeInfo @NotNull [] myComponents;

    public RefTypeInfo(@NotNull String name) {
      this(name, null, EMPTY_ARRAY);
    }

    public RefTypeInfo(@NotNull String name, @Nullable RefTypeInfo outer) {
      this(name, outer, EMPTY_ARRAY);
    }

    public RefTypeInfo(@NotNull String name, @Nullable RefTypeInfo outer, @NotNull TypeInfo @NotNull [] components) {
      super(outer != null ?
            (components.length == 0 ?
             (outer.getKind() == TypeKind.REF ? TypeKind.INNER_SIMPLE : TypeKind.INNER) : TypeKind.INNER_GENERIC) :
            (components.length == 0 ?
            // Check prefix to spare hashCode computation 
             (name.startsWith("java.") ? TEXT_TO_KIND.getOrDefault(name, TypeKind.REF) : TypeKind.REF) : 
             TypeKind.GENERIC));
      myName = name;
      myComponents = components;
      myOuter = outer;
    }

    @Override
    public String text(boolean isShort) {
      if (isShort) {
        return StringUtil.getShortName(myName);
      }
      if (myComponents.length == 0) {
        return myOuter != null ? myOuter.text(isShort) + "." + myName : myName;
      }
      StringBuilder sb = new StringBuilder();
      if (myOuter != null) {
        sb.append(myOuter.text(isShort));
        sb.append(".");
      }
      sb.append(myName);
      sb.append("<");
      for (int i = 0; i < myComponents.length; i++) {
        if (i > 0) sb.append(",");
        sb.append(myComponents[i].text());
      }
      sb.append(">");
      return sb.toString();
    }
    
    String jvmName() {
      return myOuter == null ? myName.replace('.', '/') : myOuter.jvmName() + "$" + myName;
    }

    @Override
    public int innerDepth(SignatureParsing.@NotNull TypeInfoProvider provider) {
      return myOuter != null && !provider.isKnownStatic(jvmName()) ? myOuter.innerDepth(provider) + 1 : 0;
    }

    public @NotNull RefTypeInfo withComponents(@NotNull List<TypeInfo> components) {
      return new RefTypeInfo(myName, myOuter, components.toArray(EMPTY_ARRAY));
    }

    public @NotNull RefTypeInfo withOuter(@Nullable RefTypeInfo outer) {
      if (myOuter != null) {
        return new RefTypeInfo(myName, myOuter.withOuter(outer), myComponents);
      }
      return new RefTypeInfo(myName, outer, myComponents);
    }

    /**
     * @param index index of a generic component, non-negative
     * @return corresponding component; null if there are too few components, or the type is not generic
     */
    public @Nullable TypeInfo genericComponent(int index) {
      return index >= myComponents.length ? null : myComponents[index];
    }

    /**
     * @return outer type; null if this type is not an inner type
     */
    public @Nullable RefTypeInfo outerType() {
      return myOuter;
    }
  }

  /**
   * Immediate type; fully described by its kind (primitive, void, non-parameterized wildcard)
   */
  public static final class SimpleTypeInfo extends TypeInfo {
    public static final SimpleTypeInfo NULL = new SimpleTypeInfo(TypeKind.NULL);

    public SimpleTypeInfo(@NotNull TypeKind kind) {
      super(kind);
      if (kind.isDerived() || kind.isReference()) {
        throw new IllegalArgumentException(kind.toString());
      }
    }
  }

  private TypeInfo(@NotNull TypeKind kind) {
    this.kind = kind;
  }

  String text(boolean isShort) {
    return isShort && kind.text == null ? "" : kind.text;
  }

  /**
   * @return type text (without annotations); null for {@link TypeKind#NULL} type
   */
  public final String text() {
    return text(false);
  }

  /**
   * @return type kind
   */
  public final @NotNull TypeKind getKind() {
    return kind;
  }

  /**
   * @return depth of the inner type (how many enclosing types it has)
   */
  public int innerDepth(SignatureParsing.@NotNull TypeInfoProvider provider) {
    return 0;
  }

  /**
   * @return true if this type is a vararg type
   */
  public boolean isEllipsis() {
    return kind == TypeKind.ELLIPSIS;
  }

  /**
   * @return this array type replacing the latest component with an ellipsis
   * @throws UnsupportedOperationException if this type is not an array type
   */
  public TypeInfo withEllipsis() {
    throw new UnsupportedOperationException();
  }

  /**
   * @param typeAnnotations set type annotations. Could be called only once.
   */
  public void setTypeAnnotations(@NotNull TypeAnnotationContainer typeAnnotations) {
    if (this == SimpleTypeInfo.NULL) return;
    if (myTypeAnnotations != null) {
      throw new IllegalStateException();
    }
    myTypeAnnotations = typeAnnotations;
  }

  /**
   * @return type annotations associated with this type.
   */
  public @NotNull TypeAnnotationContainer getTypeAnnotations() {
    return myTypeAnnotations == null ? TypeAnnotationContainer.EMPTY : myTypeAnnotations;
  }

  /**
   * @return short type representation (unqualified name without generic parameters)
   */
  public @NotNull String getShortTypeText() {
    return text(true);
  }

  @Override
  public String toString() {
    String text = text();
    return text != null ? text : "null";
  }

  /* factories and serialization */

  /**
   * @return return type of the constructor (null-type)
   */
  public static @NotNull TypeInfo createConstructorType() {
    return TypeInfo.SimpleTypeInfo.NULL;
  }

  /**
   * @return type created from {@link LighterAST}
   * @param tree tree structure
   * @param element element (variable, parameter, field, method, etc.) to create a type for.
   * @param parentStub parent stub element for context
   */
  public static @NotNull TypeInfo create(@NotNull LighterAST tree, @NotNull LighterASTNode element, StubElement<?> parentStub) {
    int arrayCount = 0;

    LighterASTNode typeElement = null;

    if (element.getTokenType() == JavaElementType.ENUM_CONSTANT) {
      return ((PsiClassStubImpl<?>)parentStub).getQualifiedNameTypeInfo();
    }
    for (final LighterASTNode child : tree.getChildren(element)) {
      IElementType type = child.getTokenType();
      if (type == JavaElementType.TYPE) {
        typeElement = child;
      }
      else if (type == JavaTokenType.LBRACKET) {
        arrayCount++;  // C-style array
      }
    }
    if (typeElement == null && element.getTokenType() == JavaElementType.FIELD) {
      LighterASTNode parent = tree.getParent(element);
      assert parent != null : element;
      List<LighterASTNode> fields = LightTreeUtil.getChildrenOfType(tree, parent, JavaElementType.FIELD);
      int idx = fields.indexOf(element);
      for (int i = idx - 1; i >= 0 && typeElement == null; i--) {  // int i, j
        typeElement = LightTreeUtil.firstChildOfType(tree, fields.get(i), JavaElementType.TYPE);
      }
    }

    assert typeElement != null : element + " in " + parentStub;

    TypeInfo typeInfo = fromTypeElement(tree, typeElement);
    for (int i = 0; i < arrayCount; i++) {
      typeInfo = typeInfo.arrayOf();
    }
    byte[] prefix = new byte[arrayCount];
    Arrays.fill(prefix, TypeAnnotationContainer.Collector.ARRAY_ELEMENT);
    TypeAnnotationContainer.Collector collector = new TypeAnnotationContainer.Collector(typeInfo);
    collectAnnotations(typeInfo, collector, tree, typeElement, prefix);
    collector.install();
    return typeInfo;
  }

  private static void collectAnnotations(@NotNull TypeInfo info,
                                         @NotNull TypeAnnotationContainer.Collector collector,
                                         @NotNull LighterAST tree,
                                         @NotNull LighterASTNode element,
                                         byte @NotNull [] prefix) {
    int arrayCount = 0;
    List<LighterASTNode> children = tree.getChildren(element);
    for (LighterASTNode child : children) {
      IElementType tokenType = child.getTokenType();
      if (tokenType == JavaTokenType.LBRACKET) {
        arrayCount++;
      }
    }
    int nestingLevel = 0;
    boolean bound = false;
    for (LighterASTNode child : children) {
      IElementType tokenType = child.getTokenType();
      if (tokenType == JavaTokenType.EXTENDS_KEYWORD || tokenType == JavaTokenType.SUPER_KEYWORD) {
        bound = true;
      }
      else if (tokenType == JavaElementType.JAVA_CODE_REFERENCE && info instanceof RefTypeInfo) {
        collectAnnotationsFromReference((RefTypeInfo)info, collector, tree, child, prefix);
      }
      else if (tokenType == JavaElementType.TYPE && info instanceof DerivedTypeInfo) {
        byte[] newPrefix;
        if (bound) {
          newPrefix = Arrays.copyOf(prefix, prefix.length + 1);
          newPrefix[prefix.length] = TypeAnnotationContainer.Collector.WILDCARD_BOUND;
        } else {
          newPrefix = Arrays.copyOf(prefix, prefix.length + arrayCount);
          Arrays.fill(newPrefix, prefix.length, newPrefix.length, TypeAnnotationContainer.Collector.ARRAY_ELEMENT);
        }
        collectAnnotations(((DerivedTypeInfo)info).child(), collector, tree, child, newPrefix);
      }
      else if (tokenType == JavaTokenType.LBRACKET) {
        nestingLevel++;
      }
      else if (tokenType == JavaElementType.ANNOTATION) {
        String anno = LightTreeUtil.toFilteredString(tree, child, null);
        byte[] typePath = Arrays.copyOf(prefix, prefix.length + nestingLevel);
        Arrays.fill(typePath, prefix.length, typePath.length, TypeAnnotationContainer.Collector.ARRAY_ELEMENT);
        collector.add(typePath, anno);
      }
    }
  }

  private static void collectAnnotationsFromReference(@NotNull RefTypeInfo info,
                                                      TypeAnnotationContainer.@NotNull Collector collector,
                                                      @NotNull LighterAST tree,
                                                      @NotNull LighterASTNode child,
                                                      byte @NotNull [] prefix) {
    List<LighterASTNode> refChildren = tree.getChildren(child);
    for (LighterASTNode refChild : refChildren) {
      IElementType refTokenType = refChild.getTokenType();
      if (refTokenType == JavaElementType.JAVA_CODE_REFERENCE) {
        RefTypeInfo outerType = info.outerType();
        if (outerType != null) {
          byte[] newPrefix = Arrays.copyOf(prefix, prefix.length + 1);
          newPrefix[prefix.length] = TypeAnnotationContainer.Collector.ENCLOSING_CLASS;
          collectAnnotationsFromReference(outerType, collector, tree, refChild, newPrefix);
        }
      }
      else if (refTokenType == JavaElementType.REFERENCE_PARAMETER_LIST) {
        List<LighterASTNode> subTypes = LightTreeUtil.getChildrenOfType(tree, refChild, JavaElementType.TYPE);
        if (!subTypes.isEmpty()) {
          for (int i = 0; i < subTypes.size(); i++) {
            TypeInfo componentInfo = info.genericComponent(i);
            if (componentInfo != null) {
              byte[] newPrefix = Arrays.copyOf(prefix, prefix.length + 2);
              newPrefix[prefix.length] = TypeAnnotationContainer.Collector.TYPE_ARGUMENT;
              newPrefix[prefix.length + 1] = (byte)i;
              collectAnnotations(componentInfo, collector, tree, subTypes.get(i), newPrefix);
            }
          }
        }
      }
      else if (refTokenType == JavaElementType.ANNOTATION) {
        collector.add(prefix, LightTreeUtil.toFilteredString(tree, refChild, null));
      }
    }
  }

  private static final TokenSet PRIMITIVE_TYPES =
    TokenSet.create(JavaTokenType.INT_KEYWORD, JavaTokenType.CHAR_KEYWORD, JavaTokenType.LONG_KEYWORD,
                    JavaTokenType.DOUBLE_KEYWORD, JavaTokenType.FLOAT_KEYWORD, JavaTokenType.SHORT_KEYWORD,
                    JavaTokenType.BOOLEAN_KEYWORD, JavaTokenType.BYTE_KEYWORD, JavaTokenType.VOID_KEYWORD);

  private static @NotNull TypeInfo fromTypeElement(@NotNull LighterAST tree,
                                                   @NotNull LighterASTNode typeElement) {
    TypeInfo info = null;
    TypeKind derivedKind = null;
    for (LighterASTNode child : tree.getChildren(typeElement)) {
      IElementType tokenType = child.getTokenType();
      if (PRIMITIVE_TYPES.contains(tokenType)) {
        info = new SimpleTypeInfo(TEXT_TO_KIND.get(((LighterASTTokenNode)child).getText().toString()));
      }
      else if (tokenType == JavaElementType.TYPE) {
        info = fromTypeElement(tree, child);
      }
      else if (tokenType == JavaElementType.DUMMY_ELEMENT) {
        info = fromString(LightTreeUtil.toFilteredString(tree, child, null));
      }
      else if (tokenType == JavaElementType.JAVA_CODE_REFERENCE) {
        info = fromCodeReference(tree, child);
      }
      else if (tokenType == JavaTokenType.EXTENDS_KEYWORD) {
        derivedKind = TypeKind.EXTENDS;
      }
      else if (tokenType == JavaTokenType.SUPER_KEYWORD) {
        derivedKind = TypeKind.SUPER;
      }
      else if (tokenType == JavaTokenType.QUEST) {
        info = new SimpleTypeInfo(TypeKind.WILDCARD); // may be overwritten
      }
      if (tokenType == JavaTokenType.LBRACKET) {
        info = Objects.requireNonNull(info).arrayOf();
      }
      else if (tokenType == JavaTokenType.ELLIPSIS) {
        info = Objects.requireNonNull(info).arrayOf().withEllipsis();
      }
    }
    if (info == null) {
      throw new IllegalArgumentException("Malformed type: " + LightTreeUtil.toFilteredString(tree, typeElement, null));
    }
    if (derivedKind != null) {
      info = new DerivedTypeInfo(derivedKind, info);
    }
    return info;
  }

  private static RefTypeInfo fromCodeReference(@NotNull LighterAST tree, @NotNull LighterASTNode ref) {
    RefTypeInfo info = null;
    for (LighterASTNode child : tree.getChildren(ref)) {
      IElementType tokenType = child.getTokenType();
      if (tokenType == JavaElementType.JAVA_CODE_REFERENCE) {
        info = fromCodeReference(tree, child);
      }
      else if (tokenType == JavaTokenType.IDENTIFIER) {
        String text = ((LighterASTTokenNode)child).getText().toString();
        info = new RefTypeInfo(text, info);
      }
      else if (tokenType == JavaElementType.REFERENCE_PARAMETER_LIST) {
        if (info == null) {
          throw new IllegalArgumentException("Malformed type: " + LightTreeUtil.toFilteredString(tree, ref, null));
        }
        List<TypeInfo> components = new ArrayList<>();
        for (LighterASTNode component : tree.getChildren(child)) {
          if (component.getTokenType() == JavaElementType.TYPE) {
            components.add(fromTypeElement(tree, component));
          }
        }
        info = info.withComponents(components);
      }
    }
    return info;
  }

  public @NotNull DerivedTypeInfo arrayOf() {
    return new DerivedTypeInfo(TypeKind.ARRAY, this);
  }

  /**
   * @param text type text
   * @param ellipsis if true, then the last array component will be replaced with an ellipsis 
   * @return the type created from the text
   * @deprecated avoid using it, as this method cannot correctly process inner types and actually requires parsing.
   * Instead, create the type structure explicitly, using the corresponding constructors of {@link SimpleTypeInfo}, {@link RefTypeInfo} and
   * {@link DerivedTypeInfo}.
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public static @NotNull TypeInfo fromString(@Nullable String text, boolean ellipsis) {
    TypeInfo typeInfo = fromString(text);
    return ellipsis ? typeInfo.withEllipsis() : typeInfo;
  }

  public static @NotNull TypeInfo fromString(@Nullable String text) {
    if (text == null) return TypeInfo.SimpleTypeInfo.NULL;
    TypeKind kind = TEXT_TO_KIND.get(text);
    if (kind != null) {
      return kind.isReference() ? new RefTypeInfo(text) : new SimpleTypeInfo(kind);
    }
    if (text.startsWith("? extends ")) {
      return new DerivedTypeInfo(TypeKind.EXTENDS, fromString(text.substring("? extends ".length())));
    }
    if (text.startsWith("? super ")) {
      return new DerivedTypeInfo(TypeKind.SUPER, fromString(text.substring("? super ".length())));
    }
    if (text.endsWith("[]")) {
      return fromString(text.substring(0, text.length() - 2)).arrayOf();
    }
    if (text.endsWith("...")) {
      return new DerivedTypeInfo(TypeKind.ELLIPSIS, fromString(text.substring(0, text.length() - 3)));
    }
    if (text.endsWith(">")) {
      int depth = 1;
      int end = text.length() - 1;
      List<TypeInfo> components = new ArrayList<>();
      for (int pos = end - 1; pos > 0; pos--) {
        char ch = text.charAt(pos);
        if (ch == '>') depth++;
        else if (ch == ',' && depth == 1) {
          String component = text.substring(pos + 1, end);
          end = pos;
          components.add(fromString(component));
        }
        else if (ch == '<') {
          depth--;
          if (depth == 0) {
            String component = text.substring(pos + 1, end);
            components.add(fromString(component));
            Collections.reverse(components);
            int prevGeneric = text.lastIndexOf('>', pos);
            RefTypeInfo outer;
            String name;
            if (prevGeneric > 0) {
              if (text.charAt(prevGeneric + 1) != '.') {
                throw new IllegalArgumentException("Malformed type: " + text);
              }
              outer = (RefTypeInfo)fromString(text.substring(0, prevGeneric + 1));
              name = text.substring(prevGeneric + 2, pos);
            } else {
              name = text.substring(0, pos);
              outer = null;
            }
            return new RefTypeInfo(name, outer, components.toArray(EMPTY_ARRAY));
          }
        }
      }
      throw new IllegalArgumentException("Malformed type: " + text);
    }
    return new RefTypeInfo(text);
  }

  public static @NotNull TypeInfo readTYPE(@NotNull StubInputStream record) throws IOException {
    int flags = record.readByte() & 0xFF;
    boolean hasTypeAnnotations = isSet(flags, HAS_TYPE_ANNOTATIONS);
    int kindOrdinal = clear(flags, HAS_TYPE_ANNOTATIONS);
    if (kindOrdinal >= ALL_KINDS.length) {
      throw new IOException("Unexpected TypeKind: " + flags);
    }
    TypeKind kind = ALL_KINDS[kindOrdinal];
    TypeInfo info;
    RefTypeInfo outer = null;
    switch (kind) {
      case REF:
        info = new RefTypeInfo(Objects.requireNonNull(record.readNameString()));
        break;
      case INNER_SIMPLE:
        outer = new RefTypeInfo(Objects.requireNonNull(record.readNameString()));
        info = new RefTypeInfo(Objects.requireNonNull(record.readNameString()), outer);
        break;
      case INNER:
        outer = (RefTypeInfo)readTYPE(record);
        info = new RefTypeInfo(Objects.requireNonNull(record.readNameString()), outer);
        break;
      case INNER_GENERIC:
        outer = (RefTypeInfo)readTYPE(record);
      case GENERIC:
        String name = Objects.requireNonNull(record.readNameString());
        byte count = record.readByte();
        TypeInfo[] components = new TypeInfo[count];
        for (int i = 0; i < count; i++) {
          components[i] = readTYPE(record);
        }
        info = new RefTypeInfo(name, outer, components);
        break;
      case EXTENDS:
      case SUPER:
      case ARRAY:
      case ELLIPSIS:
        info = new DerivedTypeInfo(kind, readTYPE(record));
        break;
      default:
        info = kind.isReference() ? new RefTypeInfo(Objects.requireNonNull(kind.text)) : new SimpleTypeInfo(kind);
    }
    info.setTypeAnnotations(hasTypeAnnotations ? TypeAnnotationContainer.readTypeAnnotations(record) : TypeAnnotationContainer.EMPTY);
    return info;
  }

  public static void writeTYPE(@NotNull StubOutputStream dataStream, @NotNull TypeInfo typeInfo) throws IOException {
    boolean hasTypeAnnotations = typeInfo.myTypeAnnotations != null && !typeInfo.myTypeAnnotations.isEmpty();
    dataStream.writeByte(typeInfo.kind.ordinal() | (hasTypeAnnotations ? HAS_TYPE_ANNOTATIONS : 0));

    if (typeInfo instanceof DerivedTypeInfo) {
      writeTYPE(dataStream, ((DerivedTypeInfo)typeInfo).myChild);
    }
    else if (typeInfo instanceof RefTypeInfo && typeInfo.kind.text == null) {
      if (typeInfo.kind == TypeKind.INNER_SIMPLE) {
        dataStream.writeName(Objects.requireNonNull(((RefTypeInfo)typeInfo).myOuter).myName);
      }
      if (typeInfo.kind == TypeKind.INNER || typeInfo.kind == TypeKind.INNER_GENERIC) {
        writeTYPE(dataStream, Objects.requireNonNull(((RefTypeInfo)typeInfo).myOuter));
      }
      dataStream.writeName(((RefTypeInfo)typeInfo).myName);
      if (typeInfo.kind == TypeKind.INNER_GENERIC || typeInfo.kind == TypeKind.GENERIC) {
        TypeInfo[] components = ((RefTypeInfo)typeInfo).myComponents;
        dataStream.writeByte(components.length);
        for (TypeInfo component : components) {
          writeTYPE(dataStream, component);
        }
      }
    }
    if (hasTypeAnnotations) {
      TypeAnnotationContainer.writeTypeAnnotations(dataStream, typeInfo.myTypeAnnotations);
    }
  }

  /**
   * @return type text without annotations
   * @deprecated Use simply {@link TypeInfo#text()}
   */
  @Deprecated
  public static @Nullable String createTypeText(@NotNull TypeInfo typeInfo) {
    return typeInfo.text();
  }

  public static @NotNull String internFrequentType(@NotNull String type) {
    int frequentIndex = (type.length() < 32 && (ourTypeLengthMask & (1 << type.length())) != 0) ? ourFrequentTypeIndex.getInt(type) : 0;
    return frequentIndex == 0 ? StringUtil.internEmptyString(type) : ourIndexFrequentType[frequentIndex];
  }
}