// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.impl.cache.ModifierFlags;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.*;
import com.intellij.psi.impl.java.stubs.impl.*;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.*;

import java.io.IOException;
import java.lang.reflect.Array;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.*;

import static com.intellij.openapi.util.Pair.pair;
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

  private static final int ASM_API = Opcodes.API_VERSION;

  private final T mySource;
  private final InnerClassSourceStrategy<T> myInnersStrategy;
  private final StubElement myParent;
  private final int myAccess;
  private final String myShortName;
  private final Function<String, String> myMapping;
  private final boolean myAnonymousInner;
  private final boolean myLocalClassInner;
  private String myInternalName;
  private PsiClassStub<?> myResult;
  private PsiModifierListStub myModList;

  public StubBuildingVisitor(T classSource, InnerClassSourceStrategy<T> innersStrategy, StubElement parent, int access, String shortName) {
    this(classSource, innersStrategy, parent, access, shortName, false, false);
  }

  public StubBuildingVisitor(T classSource, InnerClassSourceStrategy<T> innersStrategy, StubElement parent, int access, String shortName,
                             boolean anonymousInner, boolean localClassInner) {
    super(ASM_API);
    mySource = classSource;
    myInnersStrategy = innersStrategy;
    myParent = parent;
    myAccess = access;
    myShortName = shortName;
    myMapping = createMapping(classSource);
    myAnonymousInner = anonymousInner;
    myLocalClassInner = localClassInner;
  }

  public PsiClassStub<?> getResult() {
    return myResult;
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    myInternalName = name;
    String parentName = myParent instanceof PsiClassStub ? ((PsiClassStub)myParent).getQualifiedName() :
                        myParent instanceof PsiJavaFileStub ? ((PsiJavaFileStub)myParent).getPackageName() :
                        null;
    String fqn = myAnonymousInner || myLocalClassInner ? null : getFqn(name, myShortName, parentName);
    String shortName = myShortName != null && name.endsWith(myShortName) ? myShortName : fqn != null ? PsiNameHelper.getShortClassName(fqn) : myShortName;

    int flags = myAccess | access;
    boolean isDeprecated = isSet(flags, Opcodes.ACC_DEPRECATED);
    boolean isInterface = isSet(flags, Opcodes.ACC_INTERFACE);
    boolean isEnum = isSet(flags, Opcodes.ACC_ENUM);
    boolean isAnnotationType = isSet(flags, Opcodes.ACC_ANNOTATION);
    short stubFlags = PsiClassStubImpl.packFlags(
      isDeprecated, isInterface, isEnum, false, false, isAnnotationType, false, false, myAnonymousInner, myLocalClassInner, false, false);
    myResult = new PsiClassStubImpl(JavaStubElementTypes.CLASS, myParent, fqn, shortName, null, stubFlags);

    myModList = new PsiModifierListStubImpl(myResult, packClassFlags(flags));

    ClassInfo info = null;
    if (signature != null) {
      try {
        info = parseClassSignature(signature);
      }
      catch (ClsFormatException e) {
        if (LOG.isDebugEnabled()) LOG.debug("source=" + mySource + " signature=" + signature, e);
      }
    }
    if (info == null) {
      info = parseClassDescription(superName, interfaces);
    }

    newTypeParameterList(myResult, info.typeParameters);

    if (myResult.isInterface()) {
      if (info.interfaceNames != null && myResult.isAnnotationType()) {
        info.interfaceNames.remove(CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION);
      }
      newReferenceList(JavaStubElementTypes.EXTENDS_LIST, myResult, ArrayUtilRt.toStringArray(info.interfaceNames));
      newReferenceList(JavaStubElementTypes.IMPLEMENTS_LIST, myResult, ArrayUtilRt.EMPTY_STRING_ARRAY);
    }
    else {
      if (info.superName == null || "java/lang/Object".equals(superName) || myResult.isEnum() && "java/lang/Enum".equals(superName)) {
        newReferenceList(JavaStubElementTypes.EXTENDS_LIST, myResult, ArrayUtilRt.EMPTY_STRING_ARRAY);
      }
      else {
        newReferenceList(JavaStubElementTypes.EXTENDS_LIST, myResult, new String[]{info.superName});
      }
      newReferenceList(JavaStubElementTypes.IMPLEMENTS_LIST, myResult, ArrayUtilRt.toStringArray(info.interfaceNames));
    }
  }

  private String getFqn(@NotNull String internalName, @Nullable String shortName, @Nullable String parentName) {
    if (shortName == null || !internalName.endsWith(shortName)) {
      return myMapping.fun(internalName);
    }
    if (internalName.length() == shortName.length()) {
      return shortName;
    }
    if (parentName == null) {
      parentName = myMapping.fun(internalName.substring(0, internalName.length() - shortName.length() - 1));
    }
    return parentName + '.' + shortName;
  }

  private ClassInfo parseClassSignature(String signature) throws ClsFormatException {
    ClassInfo result = new ClassInfo();
    CharacterIterator iterator = new StringCharacterIterator(signature);
    result.typeParameters = SignatureParsing.parseTypeParametersDeclaration(iterator, myMapping);
    result.superName = SignatureParsing.parseTopLevelClassRefSignature(iterator, myMapping);
    while (iterator.current() != CharacterIterator.DONE) {
      String name = SignatureParsing.parseTopLevelClassRefSignature(iterator, myMapping);
      if (name == null) throw new ClsFormatException();
      if (result.interfaceNames == null) result.interfaceNames = new SmartList<>();
      result.interfaceNames.add(name);
    }
    return result;
  }

  private ClassInfo parseClassDescription(String superClass, String[] superInterfaces) {
    ClassInfo result = new ClassInfo();
    result.typeParameters = ContainerUtil.emptyList();
    result.superName = superClass != null ? myMapping.fun(superClass) : null;
    result.interfaceNames = superInterfaces == null ? null : ContainerUtil.map(superInterfaces, myMapping);
    return result;
  }

  private static void newTypeParameterList(StubElement<?> parent, List<Pair<String, String[]>> parameters) {
    PsiTypeParameterListStub listStub = new PsiTypeParameterListStubImpl(parent);
    for (Pair<String, String[]> parameter : parameters) {
      PsiTypeParameterStub parameterStub = new PsiTypeParameterStubImpl(listStub, parameter.first);
      newReferenceList(JavaStubElementTypes.EXTENDS_BOUND_LIST, parameterStub, parameter.second);
    }
  }

  private static void newReferenceList(@NotNull JavaClassReferenceListElementType type, StubElement parent, String @NotNull [] types) {
    new PsiClassReferenceListStubImpl(type, parent, types);
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
    ((PsiClassStubImpl)myResult).setSourceFileName(source);
  }

  @Override
  public void visitOuterClass(String owner, String name, String desc) {
    if (myParent instanceof PsiFileStub) {
      throw new OutOfOrderInnerClassException();
    }
  }

  @Override
  public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
    return new AnnotationTextCollector(desc, myMapping, text -> new PsiAnnotationStubImpl(myModList, text));
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
  public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
    if (isSet(access, Opcodes.ACC_SYNTHETIC)) return null;
    if (name == null) return null;

    byte flags = PsiFieldStubImpl.packFlags(isSet(access, Opcodes.ACC_ENUM), isSet(access, Opcodes.ACC_DEPRECATED), false, false);
    TypeInfo type = fieldType(desc, signature);
    String initializer = constToString(value, type.text, false, myMapping);
    PsiFieldStub stub = new PsiFieldStubImpl(myResult, name, type, initializer, flags);
    PsiModifierListStub modList = new PsiModifierListStubImpl(stub, packFieldFlags(access));
    return new FieldAnnotationCollectingVisitor(modList, myMapping);
  }

  private TypeInfo fieldType(String desc, String signature) {
    String type = null;
    if (signature != null) {
      try {
        type = SignatureParsing.parseTypeString(new StringCharacterIterator(signature), myMapping);
      }
      catch (ClsFormatException e) {
        if (LOG.isDebugEnabled()) LOG.debug("source=" + mySource + " signature=" + signature, e);
      }
    }
    if (type == null) {
      type = toJavaType(Type.getType(desc), myMapping);
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

    newTypeParameterList(stub, info.typeParameters);

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

    newReferenceList(JavaStubElementTypes.THROWS_LIST, stub, ArrayUtilRt.toStringArray(info.throwTypes));

    int paramIgnoreCount = isEnumConstructor ? 2 : isInnerClassConstructor ? 1 : 0;
    int localVarIgnoreCount = isEnumConstructor ? 3 : isInnerClassConstructor ? 2 : !isStatic ? 1 : 0;
    return new MethodAnnotationCollectingVisitor(stub, modList, paramStubs, paramIgnoreCount, localVarIgnoreCount, myMapping);
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

    result.typeParameters = SignatureParsing.parseTypeParametersDeclaration(iterator, myMapping);

    if (iterator.current() != '(') throw new ClsFormatException();
    iterator.next();
    if (iterator.current() == ')') {
      result.argTypes = ContainerUtil.emptyList();
    }
    else {
      result.argTypes = new SmartList<>();
      while (iterator.current() != ')' && iterator.current() != CharacterIterator.DONE) {
        result.argTypes.add(SignatureParsing.parseTypeString(iterator, myMapping));
      }
      if (iterator.current() != ')') throw new ClsFormatException();
    }
    iterator.next();

    result.returnType = SignatureParsing.parseTypeString(iterator, myMapping);

    result.throwTypes = null;
    while (iterator.current() == '^') {
      iterator.next();
      if (result.throwTypes == null) result.throwTypes = new SmartList<>();
      result.throwTypes.add(SignatureParsing.parseTypeString(iterator, myMapping));
    }
    if (exceptions != null && (result.throwTypes == null || exceptions.length > result.throwTypes.size())) {
      // a signature may be inconsistent with exception list - in this case, the more complete list takes precedence
      result.throwTypes = ContainerUtil.map(exceptions, myMapping);
    }

    return result;
  }

  private MethodInfo parseMethodDescription(String desc, String[] exceptions) {
    MethodInfo result = new MethodInfo();
    result.typeParameters = ContainerUtil.emptyList();
    result.returnType = toJavaType(Type.getReturnType(desc), myMapping);
    result.argTypes = ContainerUtil.map(Type.getArgumentTypes(desc), type -> toJavaType(type, myMapping));
    result.throwTypes = exceptions == null ? null : ContainerUtil.map(exceptions, myMapping);
    return result;
  }


  private static class ClassInfo {
    private List<Pair<String, String[]>> typeParameters;
    private String superName;
    private List<String> interfaceNames;
  }

  private static class MethodInfo {
    private List<Pair<String, String[]>> typeParameters;
    private String returnType;
    private List<String> argTypes;
    private List<String> throwTypes;
  }

  private static class AnnotationTextCollector extends AnnotationVisitor {
    private final StringBuilder myBuilder = new StringBuilder();
    private final Function<String, String> myMapping;
    private final Consumer<? super String> myCallback;
    private boolean hasPrefix;
    private boolean hasParams;

    private AnnotationTextCollector(@Nullable String desc, Function<String, String> mapping, Consumer<? super String> callback) {
      super(ASM_API);
      myMapping = mapping;
      myCallback = callback;

      if (desc != null) {
        hasPrefix = true;
        myBuilder.append('@').append(toJavaType(Type.getType(desc), myMapping));
      }
    }

    @Override
    public void visit(String name, Object value) {
      valuePairPrefix(name);
      myBuilder.append(constToString(value, null, true, myMapping));
    }

    @Override
    public void visitEnum(String name, String desc, String value) {
      valuePairPrefix(name);
      myBuilder.append(toJavaType(Type.getType(desc), myMapping)).append('.').append(value);
    }

    private void valuePairPrefix(String name) {
      if (!hasParams) {
        hasParams = true;
        if (hasPrefix) {
          myBuilder.append('(');
        }
      }
      else {
        myBuilder.append(',');
      }

      if (name != null) {
        myBuilder.append(name).append('=');
      }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String desc) {
      valuePairPrefix(name);
      return new AnnotationTextCollector(desc, myMapping, text -> myBuilder.append(text));
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      valuePairPrefix(name);
      myBuilder.append('{');
      return new AnnotationTextCollector(null, myMapping, text -> myBuilder.append(text).append('}'));
    }

    @Override
    public void visitEnd() {
      if (hasPrefix && hasParams) {
        myBuilder.append(')');
      }
      myCallback.consume(myBuilder.toString());
    }
  }

  private static class FieldAnnotationCollectingVisitor extends FieldVisitor {
    private final PsiModifierListStub myModList;
    private final Function<String, String> myMapping;
    private Set<String> myFilter;

    private FieldAnnotationCollectingVisitor(PsiModifierListStub modList, Function<String, String> mapping) {
      super(ASM_API);
      myModList = modList;
      myMapping = mapping;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return new AnnotationTextCollector(desc, myMapping, text -> {
        if (myFilter == null) myFilter = new THashSet<>();
        myFilter.add(text);
        new PsiAnnotationStubImpl(myModList, text);
      });
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
      return new AnnotationTextCollector(desc, myMapping, text -> {
        if (typePath == null && (myFilter == null || !myFilter.contains(text))) {
          new PsiAnnotationStubImpl(myModList, text);
        }
      });
    }
  }

  private static class MethodAnnotationCollectingVisitor extends MethodVisitor {
    private final PsiMethodStub myOwner;
    @NotNull
    private final PsiModifierListStub myModList;
    private final PsiParameterStubImpl[] myParamStubs;
    private final int myParamCount;
    private final int myLocalVarIgnoreCount;
    private final Function<String, String> myMapping;
    private int myParamIgnoreCount;
    private int myParamNameIndex;
    private int myUsedParamSize;
    private int myUsedParamCount;
    private List<Set<String>> myFilters;

    private MethodAnnotationCollectingVisitor(PsiMethodStub owner,
                                              @NotNull PsiModifierListStub modList,
                                              PsiParameterStubImpl[] paramStubs,
                                              int paramIgnoreCount,
                                              int localVarIgnoreCount,
                                              Function<String, String> mapping) {
      super(ASM_API);
      myOwner = owner;
      myModList = modList;
      myParamStubs = paramStubs;
      myParamCount = paramStubs.length;
      myLocalVarIgnoreCount = localVarIgnoreCount;
      myParamIgnoreCount = paramIgnoreCount;
      myMapping = mapping;
    }

    @Override
    public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
      if (myParamIgnoreCount > 0 && parameterCount == myParamCount) {
        myParamIgnoreCount = 0;
      }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return new AnnotationTextCollector(desc, myMapping, text -> {
        if (accepted(0, text)) {
          new PsiAnnotationStubImpl(myModList, text);
        }
      });
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
      return parameter < myParamIgnoreCount ? null : new AnnotationTextCollector(desc, myMapping, text -> {
        int idx = parameter - myParamIgnoreCount;
        if (accepted(idx + 1, text)) {
          new PsiAnnotationStubImpl(myOwner.findParameter(idx).getModList(), text);
        }
      });
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
      TypeReference ref = new TypeReference(typeRef);
      return new AnnotationTextCollector(desc, myMapping, text -> {
        if (ref.getSort() == TypeReference.METHOD_RETURN && typePath == null && accepted(0, text)) {
          new PsiAnnotationStubImpl(myModList, text);
        }
        else if (ref.getSort() == TypeReference.METHOD_FORMAL_PARAMETER && typePath == null) {
          int idx = ref.getFormalParameterIndex();
          if (accepted(idx + 1, text)) {
            new PsiAnnotationStubImpl(myOwner.findParameter(idx).getModList(), text);
          }
        }
      });
    }

    @Override
    public AnnotationVisitor visitAnnotationDefault() {
      return new AnnotationTextCollector(null, myMapping, text -> ((PsiMethodStubImpl)myOwner).setDefaultValueText(text));
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

    private boolean accepted(int index, String text) {
      if (myFilters == null) {
        myFilters = new ArrayList<>(myParamCount + 1);
        for (int i = 0; i < myParamCount + 1; i++) myFilters.add(null);
      }
      Set<String> filter = myFilters.get(index);
      if (filter == null) myFilters.set(index, filter = new THashSet<>());
      return filter.add(text);
    }
  }

  @Nullable
  private static String constToString(@Nullable Object value, @Nullable String type, boolean anno, Function<? super String, String> mapping) {
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

  private static String toJavaType(Type type, Function<? super String, String> mapping) {
    int dimensions = 0;
    if (type.getSort() == Type.ARRAY) {
      dimensions = type.getDimensions();
      type = type.getElementType();
    }
    String text = type.getSort() == Type.OBJECT ? mapping.fun(type.getInternalName()) : type.getClassName();
    if (dimensions > 0) text += StringUtil.repeat("[]", dimensions);
    return text;
  }

  private static Function<String, String> createMapping(Object classSource) {
    byte[] bytes = null;
    if (classSource instanceof ClsFileImpl.FileContentPair) {
      bytes = ((ClsFileImpl.FileContentPair)classSource).getContent();
    }
    else if (classSource instanceof VirtualFile) {
      try { bytes = ((VirtualFile)classSource).contentsToByteArray(false); }
      catch (IOException ignored) { }
    }

    if (bytes != null) {
      Function<String, String> mapping = createMapping(bytes);
      if (mapping != null) {
        return mapping;
      }
    }

    return GUESSING_MAPPER;
  }

  private static Function<String, String> createMapping(byte[] classBytes) {
    final Map<String, Pair<String, String>> mapping = new HashMap<>();

    try {
      new ClassReader(classBytes).accept(new ClassVisitor(ASM_API) {
        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
          if (outerName != null && innerName != null) {
            mapping.put(name, pair(outerName, innerName));
          }
        }
      }, ClsFileImpl.EMPTY_ATTRIBUTES, ClassReader.SKIP_DEBUG | ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
    }
    catch (Exception ignored) { }

    if (!mapping.isEmpty()) {
      return new Function<String, String>() {
        @Override
        public String fun(String internalName) {
          String className = internalName;

          if (className.indexOf('$') >= 0) {
            Pair<String, String> p = mapping.get(className);
            if (p == null) {
              return GUESSING_MAPPER.fun(className);
            }
            className = p.first;
            if (p.second != null) {
              className = fun(p.first) + '.' + p.second;
              mapping.put(className, pair(className, null));
            }
          }

          return className.replace('/', '.');
        }
      };
    }

    return null;
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