// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.impl.cache.ModifierFlags;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.compiled.SignatureParsing.TypeParametersDeclaration;
import com.intellij.psi.impl.java.stubs.*;
import com.intellij.psi.impl.java.stubs.impl.*;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.*;

import java.lang.reflect.Array;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.util.BitUtil.isSet;

public class StubBuildingVisitor<T> extends ClassVisitor {
  private static final Logger LOG = Logger.getInstance(StubBuildingVisitor.class);

  private static final String DOUBLE_POSITIVE_INF = "1.0 / 0.0";
  private static final String DOUBLE_NEGATIVE_INF = "-1.0 / 0.0";
  private static final String DOUBLE_NAN = "0.0d / 0.0";
  private static final String FLOAT_POSITIVE_INF = "1.0f / 0.0";
  private static final String FLOAT_NEGATIVE_INF = "-1.0f / 0.0";
  private static final String FLOAT_NAN = "0.0f / 0.0";
  private static final String SYNTHETIC_CLASS_INIT_METHOD = "<clinit>";
  private static final String SYNTHETIC_INIT_METHOD = "<init>";

  private final T mySource;
  private final InnerClassSourceStrategy<T> myInnersStrategy;
  private final StubElement<?> myParent;
  private final int myAccess;
  private final String myShortName;
  private final @NotNull FirstPassData myFirstPassData;
  private final boolean myAnonymousInner;
  private final boolean myLocalClassInner;
  private String myInternalName;
  private PsiClassStub<?> myResult;
  private PsiModifierListStub myModList;
  private PsiRecordHeaderStub myHeaderStub;
  private Map<TypeInfo, ClsTypeAnnotationCollector> myAnnoBuilders;
  private List<String> myPermittedSubclasses;
  private ClassInfo myClassInfo;

  public StubBuildingVisitor(T classSource, InnerClassSourceStrategy<T> innersStrategy, StubElement<?> parent, int access, String shortName) {
    this(classSource, innersStrategy, parent, access, shortName, false, false);
  }

  public StubBuildingVisitor(T classSource, InnerClassSourceStrategy<T> innersStrategy, StubElement<?> parent, int access, String shortName,
                             boolean anonymousInner, boolean localClassInner) {
    super(Opcodes.API_VERSION);
    mySource = classSource;
    myInnersStrategy = innersStrategy;
    myParent = parent;
    myAccess = access;
    myShortName = shortName;
    myFirstPassData = FirstPassData.create(classSource);
    myAnonymousInner = anonymousInner;
    myLocalClassInner = localClassInner;
  }

  public PsiClassStub<?> getResult() {
    return myResult;
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    myInternalName = name;
    String parentName = myParent instanceof PsiClassStub ? ((PsiClassStub<?>)myParent).getQualifiedName() :
                        myParent instanceof PsiJavaFileStub ? ((PsiJavaFileStub)myParent).getPackageName() :
                        null;
    String fqn = myAnonymousInner || myLocalClassInner ? null : getFqn(name, myShortName, parentName);
    String shortName = myShortName != null && name.endsWith(myShortName) ? myShortName : fqn != null ? PsiNameHelper.getShortClassName(fqn) : myShortName;

    int flags = myAccess | access;
    boolean isDeprecated = isSet(flags, Opcodes.ACC_DEPRECATED);
    boolean isInterface = isSet(flags, Opcodes.ACC_INTERFACE);
    boolean isEnum = isSet(flags, Opcodes.ACC_ENUM);
    boolean isAnnotationType = isSet(flags, Opcodes.ACC_ANNOTATION);
    boolean isRecord = isSet(flags, Opcodes.ACC_RECORD);
    short stubFlags = PsiClassStubImpl.packFlags(
      isDeprecated, isInterface, isEnum, false, false, isAnnotationType, false, false, myAnonymousInner, myLocalClassInner, false,
      isRecord);
    myResult = new PsiClassStubImpl<>(JavaStubElementTypes.CLASS, myParent, fqn, shortName, null, stubFlags);

    int classFlags = packClassFlags(flags);
    if (myFirstPassData.isSealed()) {
      classFlags |= ModifierFlags.SEALED_MASK;
    }
    myModList = new PsiModifierListStubImpl(myResult, classFlags);
    if (isRecord) {
      myHeaderStub = new PsiRecordHeaderStubImpl(myResult);
    }

    if (signature != null) {
      try {
        myClassInfo = parseClassSignature(signature);
      }
      catch (ClsFormatException e) {
        if (LOG.isDebugEnabled()) LOG.debug("source=" + mySource + " signature=" + signature, e);
      }
    }
    if (myClassInfo == null) {
      myClassInfo = parseClassDescription(superName, interfaces);
    }

    new PsiTypeParameterListStubImpl(myResult);

    if (myResult.isInterface()) {
      if (myClassInfo.interfaces != null && myResult.isAnnotationType()) {
        myClassInfo.interfaces.removeIf(info -> info.text.equals(CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION));
      }
      newReferenceList(JavaStubElementTypes.EXTENDS_LIST, myResult, myClassInfo.interfaces);
      newReferenceList(JavaStubElementTypes.IMPLEMENTS_LIST, myResult, Collections.emptyList());
    }
    else {
      if (myClassInfo.superType == null || 
          myResult.isEnum() && "java/lang/Enum".equals(superName) ||
          myResult.isRecord() && "java/lang/Record".equals(superName)) {
        newReferenceList(JavaStubElementTypes.EXTENDS_LIST, myResult, Collections.emptyList());
      }
      else {
        newReferenceList(JavaStubElementTypes.EXTENDS_LIST, myResult, Collections.singletonList(myClassInfo.superType));
      }
      newReferenceList(JavaStubElementTypes.IMPLEMENTS_LIST, myResult, myClassInfo.interfaces);
    }
  }

  @Override
  public void visitPermittedSubclass(String permittedSubclass) {
    if (myPermittedSubclasses == null) {
      myPermittedSubclasses = new SmartList<>();
    }
    myPermittedSubclasses.add(permittedSubclass);
  }

  private String getFqn(@NotNull String internalName, @Nullable String shortName, @Nullable String parentName) {
    if (shortName == null || !internalName.endsWith(shortName)) {
      return myFirstPassData.mapJvmClassNameToJava(internalName);
    }
    if (internalName.length() == shortName.length()) {
      return shortName;
    }
    if (parentName == null) {
      parentName = myFirstPassData.mapJvmClassNameToJava(internalName.substring(0, internalName.length() - shortName.length() - 1));
    }
    return parentName + '.' + shortName;
  }

  private ClassInfo parseClassSignature(String signature) throws ClsFormatException {
    ClassInfo result = new ClassInfo();
    CharacterIterator iterator = new StringCharacterIterator(signature);
    result.typeParameters = SignatureParsing.parseTypeParametersDeclaration(iterator, myFirstPassData);
    String superName = SignatureParsing.parseTopLevelClassRefSignature(iterator, myFirstPassData);
    result.superType = superName == null ? null : new TypeInfo(superName);
    while (iterator.current() != CharacterIterator.DONE) {
      String name = SignatureParsing.parseTopLevelClassRefSignature(iterator, myFirstPassData);
      if (name == null) throw new ClsFormatException();
      if (result.interfaces == null) result.interfaces = new SmartList<>();
      result.interfaces.add(new TypeInfo(name));
    }
    return result;
  }

  private ClassInfo parseClassDescription(String superClass, String[] superInterfaces) {
    ClassInfo result = new ClassInfo();
    result.typeParameters = TypeParametersDeclaration.EMPTY;
    result.superType = superClass != null ? new TypeInfo(myFirstPassData.mapJvmClassNameToJava(superClass, false)) : null;
    result.interfaces = myFirstPassData.createTypes(superInterfaces);
    return result;
  }

  private static void newReferenceList(@NotNull JavaClassReferenceListElementType type, StubElement<?> parent, @Nullable List<TypeInfo> types) {
    new PsiClassReferenceListStubImpl(type, parent, types == null ? TypeInfo.EMPTY_ARRAY : types.toArray(TypeInfo.EMPTY_ARRAY));
  }

  private static int packCommonFlags(int access) {
    int flags = 0;

    if (isSet(access, Opcodes.ACC_PRIVATE)) flags |= ModifierFlags.PRIVATE_MASK;
    else if (isSet(access, Opcodes.ACC_PROTECTED)) flags |= ModifierFlags.PROTECTED_MASK;
    else if (isSet(access, Opcodes.ACC_PUBLIC)) flags |= ModifierFlags.PUBLIC_MASK;
    else flags |= ModifierFlags.PACKAGE_LOCAL_MASK;

    if (isSet(access, Opcodes.ACC_STATIC)) flags |= ModifierFlags.STATIC_MASK;
    if (isSet(access, Opcodes.ACC_FINAL)) flags |= ModifierFlags.FINAL_MASK;

    return flags;
  }

  private static int packClassFlags(int access) {
    int flags = packCommonFlags(access);
    if (isSet(access, Opcodes.ACC_ABSTRACT)) flags |= ModifierFlags.ABSTRACT_MASK;
    return flags;
  }

  private static int packFieldFlags(int access) {
    int flags = packCommonFlags(access);
    if (isSet(access, Opcodes.ACC_VOLATILE)) flags |= ModifierFlags.VOLATILE_MASK;
    if (isSet(access, Opcodes.ACC_TRANSIENT)) flags |= ModifierFlags.TRANSIENT_MASK;
    return flags;
  }

  private static int packMethodFlags(int access, boolean isInterface) {
    int flags = packCommonFlags(access);

    if (isSet(access, Opcodes.ACC_SYNCHRONIZED)) flags |= ModifierFlags.SYNCHRONIZED_MASK;
    if (isSet(access, Opcodes.ACC_NATIVE)) flags |= ModifierFlags.NATIVE_MASK;
    if (isSet(access, Opcodes.ACC_STRICT)) flags |= ModifierFlags.STRICTFP_MASK;

    if (isSet(access, Opcodes.ACC_ABSTRACT)) flags |= ModifierFlags.ABSTRACT_MASK;
    else if (isInterface && !isSet(access, Opcodes.ACC_STATIC)) flags |= ModifierFlags.DEFAULT_MASK;

    return flags;
  }

  @Override
  public void visitSource(String source, String debug) {
    ((PsiClassStubImpl<?>)myResult).setSourceFileName(source);
  }

  @Override
  public void visitOuterClass(String owner, String name, String desc) {
    if (myParent instanceof PsiFileStub) {
      throw new OutOfOrderInnerClassException();
    }
  }

  @Override
  public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
    return new AnnotationTextCollector(desc, myFirstPassData, text -> new PsiAnnotationStubImpl(myModList, text));
  }

  @Override
  public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
    TypeReference ref = new TypeReference(typeRef);
    TypeInfo info = null;
    if (ref.getSort() == TypeReference.CLASS_TYPE_PARAMETER_BOUND) {
      info = myClassInfo.typeParameters.getBoundType(ref);
    }
    else if (ref.getSort() == TypeReference.CLASS_TYPE_PARAMETER) {
      info = myClassInfo.typeParameters.getParameterType(ref);
    }
    else if (ref.getSort() == TypeReference.CLASS_EXTENDS) {
      int index = ref.getSuperTypeIndex();
      if (index == -1) {
        info = myClassInfo.superType;
      }
      else if (index >= 0 && index < myClassInfo.interfaces.size()) {
        info = myClassInfo.interfaces.get(index);
      }
    }
    if (info == null) return null;
    if (myAnnoBuilders == null) {
      myAnnoBuilders = new HashMap<>();
    }
    return myAnnoBuilders.computeIfAbsent(info, typeInfo -> new ClsTypeAnnotationCollector(typeInfo, myFirstPassData))
      .collect(typePath, desc);
  }

  @Override
  public void visitEnd() {
    if (myAnnoBuilders != null) {
      myAnnoBuilders.values().forEach(ClsTypeAnnotationCollector::install);
    }
    myClassInfo.typeParameters.fillInTypeParameterList(myResult);
    if (myPermittedSubclasses != null) {
      List<TypeInfo> permittedTypes = myFirstPassData.createTypes(ArrayUtil.toStringArray(myPermittedSubclasses));
      newReferenceList(JavaStubElementTypes.PERMITS_LIST, myResult, permittedTypes);
    }
  }

  @Override
  public void visitInnerClass(String name, String outerName, String innerName, int access) {
    if (isSet(access, Opcodes.ACC_SYNTHETIC)) return;
    String jvmClassName = innerName;

    boolean isAnonymousInner = innerName == null;
    boolean isLocalClassInner = !isAnonymousInner && outerName == null;

    if (innerName == null || outerName == null) {
      int $index;
      if (myInternalName.equals(name) || ($index = name.lastIndexOf('$')) == -1) {
        return;
      }
      else if (isAnonymousInner) {
        jvmClassName = name.substring($index + 1);
        innerName = jvmClassName;
        outerName = name.substring(0, $index);
      }
      else { // isLocalClassInner
        outerName = name.substring(0, $index);
        jvmClassName = name.substring($index + 1);
      }
    }

    if (myParent instanceof PsiFileStub && myInternalName.equals(name)) {
      throw new OutOfOrderInnerClassException();  // our result is inner class
    }

    if (myInternalName.equals(outerName)) {
      T innerClass = myInnersStrategy.findInnerClass(jvmClassName, mySource);
      if (innerClass != null) {
        StubBuildingVisitor<T> visitor =
          new StubBuildingVisitor<>(innerClass, myInnersStrategy, myResult, access, innerName, isAnonymousInner, isLocalClassInner);
        myInnersStrategy.accept(innerClass, visitor);
      }
    }
  }

  @Override
  public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
    if (myHeaderStub == null) return null;
    boolean isEllipsis = myFirstPassData.isVarArgComponent(name);
    byte flags = PsiRecordComponentStubImpl.packFlags(isEllipsis, false);
    TypeInfo type = fieldType(descriptor, signature);
    if (isEllipsis) {
      type = new TypeInfo(type.text, type.arrayCount, true);
    }
    PsiRecordComponentStubImpl stub = new PsiRecordComponentStubImpl(myHeaderStub, name, type, flags);
    PsiModifierListStub modList = new PsiModifierListStubImpl(stub, 0);
    return new RecordComponentAnnotationCollectingVisitor(stub, modList, myFirstPassData);
  }

  @Override
  public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
    if (isSet(access, Opcodes.ACC_SYNTHETIC)) return null;
    if (name == null) return null;
    if (myResult.isRecord() && access == (Opcodes.ACC_FINAL | Opcodes.ACC_PRIVATE)) return null;

    byte flags = PsiFieldStubImpl.packFlags(isSet(access, Opcodes.ACC_ENUM), isSet(access, Opcodes.ACC_DEPRECATED), false, false);
    TypeInfo type = fieldType(desc, signature);
    String initializer = constToString(value, type.text, false, myFirstPassData);
    PsiFieldStub stub = new PsiFieldStubImpl(myResult, name, type, initializer, flags);
    PsiModifierListStub modList = new PsiModifierListStubImpl(stub, packFieldFlags(access));
    return new FieldAnnotationCollectingVisitor(stub, modList, myFirstPassData);
  }

  private TypeInfo fieldType(String desc, String signature) {
    String type = null;
    if (signature != null) {
      try {
        type = SignatureParsing.parseTypeString(new StringCharacterIterator(signature), myFirstPassData);
      }
      catch (ClsFormatException e) {
        if (LOG.isDebugEnabled()) LOG.debug("source=" + mySource + " signature=" + signature, e);
      }
    }
    if (type == null) {
      type = toJavaType(Type.getType(desc), myFirstPassData);
    }
    return TypeInfo.fromString(type, false);
  }

  private static final String[] parameterNames = {"p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9"};

  @Override
  public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
    if (isSet(access, Opcodes.ACC_SYNTHETIC) || name == null || SYNTHETIC_CLASS_INIT_METHOD.equals(name)) return null;

    boolean isConstructor = SYNTHETIC_INIT_METHOD.equals(name);
    if (isConstructor && myAnonymousInner) return null;

    boolean isEnum = myResult.isEnum();
    if (isEnum) {
      if ("values".equals(name) && desc.startsWith("()")) return null;
      if ("valueOf".equals(name) && desc.startsWith("(Ljava/lang/String;)")) return null;
    }
    
    if (myFirstPassData.isSyntheticRecordMethod(name, desc)) return null;

    boolean isDeprecated = isSet(access, Opcodes.ACC_DEPRECATED);
    boolean isVarargs = isSet(access, Opcodes.ACC_VARARGS);
    boolean isStatic = isSet(access, Opcodes.ACC_STATIC);
    boolean isAnnotationMethod = myResult.isAnnotationType();

    byte flags = PsiMethodStubImpl.packFlags(isConstructor, isAnnotationMethod, isVarargs, isDeprecated, false, false);

    String canonicalMethodName = isConstructor ? myResult.getName() : name;

    MethodInfo info = null;
    boolean hasSignature = false;
    if (signature != null) {
      try {
        info = parseMethodSignature(signature, exceptions);
        hasSignature = true;
      }
      catch (ClsFormatException e) {
        if (LOG.isDebugEnabled()) LOG.debug("source=" + mySource + " signature=" + signature, e);
      }
    }
    if (info == null) {
      info = parseMethodDescription(desc, exceptions);
    }

    PsiMethodStubImpl stub = new PsiMethodStubImpl(myResult, canonicalMethodName, TypeInfo.fromString(info.returnType, false), flags, null);

    PsiModifierListStub modList = new PsiModifierListStubImpl(stub, packMethodFlags(access, myResult.isInterface()));

    new PsiTypeParameterListStubImpl(stub);

    boolean isEnumConstructor = isConstructor && isEnum;
    boolean isInnerClassConstructor = isConstructor && !isEnum && isInner() && !isGroovyClosure(canonicalMethodName);

    List<String> args = info.argTypes;
    if (!hasSignature) {
      if (isEnumConstructor && args.size() >= 2 && CommonClassNames.JAVA_LANG_STRING.equals(args.get(0)) && "int".equals(args.get(1))) {
        args = args.subList(2, args.size());  // omit synthetic enum constructor parameters
      }
      else if (isInnerClassConstructor && args.size() >= 1) {
        args = args.subList(1, args.size());  // omit synthetic inner class constructor parameter
      }
    }

    PsiParameterListStubImpl parameterList = new PsiParameterListStubImpl(stub);
    int paramCount = args.size();
    PsiParameterStubImpl[] paramStubs = new PsiParameterStubImpl[paramCount];
    for (int i = 0; i < paramCount; i++) {
      String arg = args.get(i);
      boolean isEllipsisParam = isVarargs && i == paramCount - 1;
      TypeInfo typeInfo = TypeInfo.fromString(arg, isEllipsisParam);

      String paramName = i < parameterNames.length ? parameterNames[i] : "p" + (i + 1);
      PsiParameterStubImpl parameterStub = new PsiParameterStubImpl(parameterList, paramName, typeInfo, isEllipsisParam, true);
      paramStubs[i] = parameterStub;
      new PsiModifierListStubImpl(parameterStub, 0);
    }

    newReferenceList(JavaStubElementTypes.THROWS_LIST, stub, info.throwTypes);

    int paramIgnoreCount = isEnumConstructor ? 2 : isInnerClassConstructor ? 1 : 0;
    int localVarIgnoreCount = isEnumConstructor ? 3 : isInnerClassConstructor ? 2 : !isStatic ? 1 : 0;
    return new MethodAnnotationCollectingVisitor(stub, info, modList, paramStubs, paramIgnoreCount, localVarIgnoreCount, myFirstPassData);
  }

  private boolean isInner() {
    return myParent instanceof PsiClassStub && !isSet(myModList.getModifiersMask(), Opcodes.ACC_STATIC);
  }

  private boolean isGroovyClosure(String canonicalMethodName) {
    if (canonicalMethodName != null && canonicalMethodName.startsWith("_closure")) {
      PsiClassReferenceListStub extendsList = myResult.findChildStubByType(JavaStubElementTypes.EXTENDS_LIST);
      if (extendsList != null) {
        String[] names = extendsList.getReferencedNames();
        return names.length == 1 && "groovy.lang.Closure".equals(names[0]);
      }
    }

    return false;
  }

  private MethodInfo parseMethodSignature(String signature, String[] exceptions) throws ClsFormatException {
    MethodInfo result = new MethodInfo();
    CharacterIterator iterator = new StringCharacterIterator(signature);

    result.typeParameters = SignatureParsing.parseTypeParametersDeclaration(iterator, myFirstPassData);

    if (iterator.current() != '(') throw new ClsFormatException();
    iterator.next();
    if (iterator.current() == ')') {
      result.argTypes = ContainerUtil.emptyList();
    }
    else {
      result.argTypes = new SmartList<>();
      while (iterator.current() != ')' && iterator.current() != CharacterIterator.DONE) {
        result.argTypes.add(SignatureParsing.parseTypeString(iterator, myFirstPassData));
      }
      if (iterator.current() != ')') throw new ClsFormatException();
    }
    iterator.next();

    result.returnType = SignatureParsing.parseTypeString(iterator, myFirstPassData);

    result.throwTypes = null;
    while (iterator.current() == '^') {
      iterator.next();
      if (result.throwTypes == null) result.throwTypes = new SmartList<>();
      result.throwTypes.add(new TypeInfo(SignatureParsing.parseTypeString(iterator, myFirstPassData)));
    }
    if (exceptions != null && (result.throwTypes == null || exceptions.length > result.throwTypes.size())) {
      // a signature may be inconsistent with exception list - in this case, the more complete list takes precedence
      result.throwTypes = myFirstPassData.createTypes(exceptions);
    }

    return result;
  }

  private MethodInfo parseMethodDescription(String desc, String[] exceptions) {
    MethodInfo result = new MethodInfo();
    result.typeParameters = TypeParametersDeclaration.EMPTY;
    result.returnType = toJavaType(Type.getReturnType(desc), myFirstPassData);
    result.argTypes = ContainerUtil.map(Type.getArgumentTypes(desc), type -> toJavaType(type, myFirstPassData));
    result.throwTypes = myFirstPassData.createTypes(exceptions);
    return result;
  }


  private static class ClassInfo {
    private TypeParametersDeclaration typeParameters;
    private TypeInfo superType;
    private List<TypeInfo> interfaces;
  }

  private static class MethodInfo {
    private TypeParametersDeclaration typeParameters;
    private String returnType;
    private List<String> argTypes;
    private List<TypeInfo> throwTypes;
  }

  private static final class FieldAnnotationCollectingVisitor extends FieldVisitor {
    private final @NotNull PsiModifierListStub myModList;
    private final @NotNull FirstPassData myFirstPassData;
    private final @NotNull ClsTypeAnnotationCollector myAnnoBuilder;

    private FieldAnnotationCollectingVisitor(@NotNull PsiFieldStub stub,
                                             @NotNull PsiModifierListStub modList,
                                             @NotNull FirstPassData firstPassData) {
      super(Opcodes.API_VERSION);
      myModList = modList;
      myFirstPassData = firstPassData;
      myAnnoBuilder = new ClsTypeAnnotationCollector(stub.getType(), firstPassData);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return new AnnotationTextCollector(desc, myFirstPassData, text -> {
        new PsiAnnotationStubImpl(myModList, text);
      });
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
      return myAnnoBuilder.collect(typePath, desc);
    }

    @Override
    public void visitEnd() {
      myAnnoBuilder.install();
    }
  }

  private static final class RecordComponentAnnotationCollectingVisitor extends RecordComponentVisitor {
    private final @NotNull PsiModifierListStub myModList;
    private final @NotNull FirstPassData myFirstPassData;
    private final @NotNull ClsTypeAnnotationCollector myAnnoBuilder;

    private RecordComponentAnnotationCollectingVisitor(@NotNull PsiRecordComponentStub stub,
                                                       @NotNull PsiModifierListStub modList,
                                                       @NotNull FirstPassData firstPassData) {
      super(Opcodes.API_VERSION);
      myModList = modList;
      myFirstPassData = firstPassData;
      myAnnoBuilder = new ClsTypeAnnotationCollector(stub.getType(), firstPassData);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return new AnnotationTextCollector(desc, myFirstPassData, text -> {
        new PsiAnnotationStubImpl(myModList, text);
      });
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
      return myAnnoBuilder.collect(typePath, desc);
    }

    @Override
    public void visitEnd() {
      myAnnoBuilder.install();
    }
  }

  private static final class MethodAnnotationCollectingVisitor extends MethodVisitor {
    private final PsiMethodStub myOwner;
    private final @NotNull MethodInfo myMethodInfo;
    private final @NotNull PsiModifierListStub myModList;
    private final PsiParameterStubImpl[] myParamStubs;
    private final int myParamCount;
    private final int myLocalVarIgnoreCount;
    private final @NotNull FirstPassData myFirstPassData;
    private int myParamIgnoreCount;
    private int myParamNameIndex;
    private int myUsedParamSize;
    private int myUsedParamCount;
    private Map<TypeInfo, ClsTypeAnnotationCollector> myAnnoBuilders;

    private MethodAnnotationCollectingVisitor(PsiMethodStub owner,
                                              @NotNull MethodInfo methodInfo,
                                              @NotNull PsiModifierListStub modList,
                                              PsiParameterStubImpl[] paramStubs,
                                              int paramIgnoreCount,
                                              int localVarIgnoreCount,
                                              @NotNull FirstPassData firstPassData) {
      super(Opcodes.API_VERSION);
      myOwner = owner;
      myMethodInfo = methodInfo;
      myModList = modList;
      myParamStubs = paramStubs;
      myParamCount = paramStubs.length;
      myLocalVarIgnoreCount = localVarIgnoreCount;
      myParamIgnoreCount = paramIgnoreCount;
      myFirstPassData = firstPassData;
    }

    @Override
    public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
      if (myParamIgnoreCount > 0 && parameterCount == myParamCount) {
        myParamIgnoreCount = 0;
      }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return new AnnotationTextCollector(desc, myFirstPassData, text -> {
        new PsiAnnotationStubImpl(myModList, text);
      });
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
      return parameter < myParamIgnoreCount ? null : new AnnotationTextCollector(desc, myFirstPassData, text -> {
        int idx = parameter - myParamIgnoreCount;
        new PsiAnnotationStubImpl(myOwner.findParameter(idx).getModList(), text);
      });
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
      TypeReference ref = new TypeReference(typeRef);
      TypeInfo info = null;
      if (ref.getSort() == TypeReference.METHOD_RETURN) {
        info = myOwner.getReturnTypeText();
      }
      else if (ref.getSort() == TypeReference.METHOD_FORMAL_PARAMETER) {
        int parameterIndex = ref.getFormalParameterIndex();
        if (parameterIndex < myParamStubs.length) {
          info = myParamStubs[parameterIndex].getType();
        }
      }
      else if (ref.getSort() == TypeReference.METHOD_TYPE_PARAMETER) {
        info = myMethodInfo.typeParameters.getParameterType(ref);
      }
      else if (ref.getSort() == TypeReference.METHOD_TYPE_PARAMETER_BOUND) {
        info = myMethodInfo.typeParameters.getBoundType(ref);
      }
      else if (ref.getSort() == TypeReference.THROWS) {
        int index = ref.getExceptionIndex();
        if (index < myMethodInfo.throwTypes.size()) {
          info = myMethodInfo.throwTypes.get(index);
        }
      }
      if (info == null) return null;
      if (myAnnoBuilders == null) {
        myAnnoBuilders = new HashMap<>();
      }
      return myAnnoBuilders.computeIfAbsent(info, typeInfo -> new ClsTypeAnnotationCollector(typeInfo, myFirstPassData))
        .collect(typePath, desc);
    }

    @Override
    public void visitEnd() {
      if (myAnnoBuilders != null) {
        myAnnoBuilders.values().forEach(ClsTypeAnnotationCollector::install);
      }
      myMethodInfo.typeParameters.fillInTypeParameterList(myOwner);
    }

    @Override
    public AnnotationVisitor visitAnnotationDefault() {
      return new AnnotationTextCollector(null, myFirstPassData, text -> ((PsiMethodStubImpl)myOwner).setDefaultValueText(text));
    }

    @Override
    public void visitParameter(String name, int access) {
      int paramIndex = myParamNameIndex++ - myParamIgnoreCount;
      if (!isSet(access, Opcodes.ACC_SYNTHETIC) && paramIndex >= 0 && paramIndex < myParamCount) {
        setParameterName(name, paramIndex);
      }
    }

    @Override
    public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
      if (index >= myLocalVarIgnoreCount) {
        int paramIndex = index - myLocalVarIgnoreCount == myUsedParamSize ? myUsedParamCount : index - myLocalVarIgnoreCount;
        if (paramIndex < myParamCount) {
          setParameterName(name, paramIndex);
          myUsedParamCount = paramIndex + 1;
          myUsedParamSize += "D".equals(desc) || "J".equals(desc) ? 2 : 1;
        }
      }
    }

    private void setParameterName(String name, int paramIndex) {
      if (ClsParsingUtil.isJavaIdentifier(name, LanguageLevel.HIGHEST)) {
        myParamStubs[paramIndex].setName(name);
      }
    }
  }

  @Nullable
  static String constToString(@Nullable Object value, @Nullable String type, boolean anno, Function<? super String, String> mapping) {
    if (value == null) return null;

    if (value instanceof String) {
      return "\"" + StringUtil.escapeStringCharacters((String)value) + "\"";
    }

    if (value instanceof Boolean || value instanceof Short || value instanceof Byte) {
      return value.toString();
    }

    if (value instanceof Character) {
      return "'" + StringUtil.escapeCharCharacters(value.toString()) + "'";
    }

    if (value instanceof Long) {
      return value.toString() + 'L';
    }

    if (value instanceof Integer) {
      if ("boolean".equals(type)) {
        if (value.equals(0)) return "false";
        if (value.equals(1)) return "true";
      }
      if ("char".equals(type)) {
        char ch = (char)((Integer)value).intValue();
        return "'" + StringUtil.escapeCharCharacters(String.valueOf(ch)) + "'";
      }
      return value.toString();
    }

    if (value instanceof Double) {
      double d = (Double)value;
      if (Double.isInfinite(d)) {
        return d > 0 ? DOUBLE_POSITIVE_INF : DOUBLE_NEGATIVE_INF;
      }
      if (Double.isNaN(d)) {
        return DOUBLE_NAN;
      }
      return Double.toString(d);
    }

    if (value instanceof Float) {
      float v = (Float)value;

      if (Float.isInfinite(v)) {
        return v > 0 ? FLOAT_POSITIVE_INF : FLOAT_NEGATIVE_INF;
      }
      else if (Float.isNaN(v)) {
        return FLOAT_NAN;
      }
      else {
        return Float.toString(v) + 'f';
      }
    }

    if (value.getClass().isArray()) {
      StringBuilder buffer = new StringBuilder();
      buffer.append('{');
      for (int i = 0, length = Array.getLength(value); i < length; i++) {
        if (i > 0) buffer.append(", ");
        buffer.append(constToString(Array.get(value, i), type, anno, mapping));
      }
      buffer.append('}');
      return buffer.toString();
    }

    if (anno && value instanceof Type) {
      return toJavaType((Type)value, mapping) + ".class";
    }

    return null;
  }

  static String toJavaType(Type type, @NotNull Function<? super String, String> mapping) {
    int dimensions = 0;
    if (type.getSort() == Type.ARRAY) {
      dimensions = type.getDimensions();
      type = type.getElementType();
    }
    String text = type.getSort() == Type.OBJECT ? mapping.fun(type.getInternalName()) : type.getClassName();
    if (dimensions > 0) text += StringUtil.repeat("[]", dimensions);
    return text;
  }

  public static final Function<String, String> GUESSING_MAPPER = internalName -> {
    String canonicalText = internalName;

    if (canonicalText.indexOf('$') >= 0) {
      StringBuilder sb = new StringBuilder(canonicalText);
      boolean updated = false;
      int start = canonicalText.lastIndexOf('/') + 2; // -1 => 1 if no package; skip first char in class name
      for (int p = start; p < sb.length(); p++) {
        char c = sb.charAt(p);
        if (c == '$' && p < sb.length() - 1 && sb.charAt(p + 1) != '$') {
          sb.setCharAt(p, '.');
          updated = true;
        }
      }
      if (updated) {
        canonicalText = sb.toString();
      }
    }

    return canonicalText.replace('/', '.');
  };

  public static AnnotationVisitor getAnnotationTextCollector(String desc, Consumer<? super String> consumer) {
    return new AnnotationTextCollector(desc, GUESSING_MAPPER, consumer);
  }
}