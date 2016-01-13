/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.impl.cache.ModifierFlags;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.*;
import com.intellij.psi.impl.java.stubs.impl.*;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.cls.ClsFormatException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.*;

import java.lang.reflect.Array;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

/**
 * @author max
 */
public class StubBuildingVisitor<T> extends ClassVisitor {
  private static final Pattern REGEX_PATTERN = Pattern.compile("(?<=[^\\$\\.])\\$(?=[^\\$])"); // disallow .$ or $$

  public static final String DOUBLE_POSITIVE_INF = "1.0 / 0.0";
  public static final String DOUBLE_NEGATIVE_INF = "-1.0 / 0.0";
  public static final String DOUBLE_NAN = "0.0d / 0.0";

  public static final String FLOAT_POSITIVE_INF = "1.0f / 0.0";
  public static final String FLOAT_NEGATIVE_INF = "-1.0f / 0.0";
  public static final String FLOAT_NAN = "0.0f / 0.0";

  private static final int ASM_API = Opcodes.ASM5;

  private static final String SYNTHETIC_CLASS_INIT_METHOD = "<clinit>";
  private static final String SYNTHETIC_INIT_METHOD = "<init>";

  private final T mySource;
  private final InnerClassSourceStrategy<T> myInnersStrategy;
  private final StubElement myParent;
  private final int myAccess;
  private final String myShortName;
  private String myInternalName;
  private PsiClassStub myResult;
  private PsiModifierListStub myModList;

  public StubBuildingVisitor(T classSource, InnerClassSourceStrategy<T> innersStrategy, StubElement parent, int access, String shortName) {
    super(ASM_API);
    mySource = classSource;
    myInnersStrategy = innersStrategy;
    myParent = parent;
    myAccess = access;
    myShortName = shortName;
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
    String fqn = getFqn(name, myShortName, parentName);
    String shortName = myShortName != null && name.endsWith(myShortName) ? myShortName : PsiNameHelper.getShortClassName(fqn);

    int flags = myAccess | access;
    boolean isDeprecated = (flags & Opcodes.ACC_DEPRECATED) != 0;
    boolean isInterface = (flags & Opcodes.ACC_INTERFACE) != 0;
    boolean isEnum = (flags & Opcodes.ACC_ENUM) != 0;
    boolean isAnnotationType = (flags & Opcodes.ACC_ANNOTATION) != 0;

    byte stubFlags = PsiClassStubImpl.packFlags(isDeprecated, isInterface, isEnum, false, false, isAnnotationType, false, false);
    myResult = new PsiClassStubImpl(JavaStubElementTypes.CLASS, myParent, fqn, shortName, null, stubFlags);

    LanguageLevel languageLevel = ClsParsingUtil.getLanguageLevelByVersion(version);
    if (languageLevel == null) languageLevel = LanguageLevel.HIGHEST;
    ((PsiClassStubImpl)myResult).setLanguageLevel(languageLevel);

    myModList = new PsiModifierListStubImpl(myResult, packClassFlags(flags));

    CharacterIterator signatureIterator = signature != null ? new StringCharacterIterator(signature) : null;
    if (signatureIterator != null) {
      try {
        SignatureParsing.parseTypeParametersDeclaration(signatureIterator, myResult);
      }
      catch (ClsFormatException e) {
        signatureIterator = null;
      }
    }
    else {
      new PsiTypeParameterListStubImpl(myResult);
    }

    String convertedSuper;
    List<String> convertedInterfaces = new ArrayList<String>();
    if (signatureIterator == null) {
      convertedSuper = parseClassDescription(superName, interfaces, convertedInterfaces);
    }
    else {
      try {
        convertedSuper = parseClassSignature(signatureIterator, convertedInterfaces);
      }
      catch (ClsFormatException e) {
        new PsiTypeParameterListStubImpl(myResult);
        convertedSuper = parseClassDescription(superName, interfaces, convertedInterfaces);
      }
    }

    if (isInterface) {
      if (isAnnotationType) {
        convertedInterfaces.remove(JAVA_LANG_ANNOTATION_ANNOTATION);
      }
      newReferenceList(JavaStubElementTypes.EXTENDS_LIST, myResult, ArrayUtil.toStringArray(convertedInterfaces));
      newReferenceList(JavaStubElementTypes.IMPLEMENTS_LIST, myResult, ArrayUtil.EMPTY_STRING_ARRAY);
    }
    else {
      if (convertedSuper == null || "java/lang/Object".equals(superName) || isEnum && "java/lang/Enum".equals(superName)) {
        newReferenceList(JavaStubElementTypes.EXTENDS_LIST, myResult, ArrayUtil.EMPTY_STRING_ARRAY);
      }
      else {
        newReferenceList(JavaStubElementTypes.EXTENDS_LIST, myResult, new String[]{convertedSuper});
      }
      newReferenceList(JavaStubElementTypes.IMPLEMENTS_LIST, myResult, ArrayUtil.toStringArray(convertedInterfaces));
    }
  }

  private static String getFqn(@NotNull String internalName, @Nullable String shortName, @Nullable String parentName) {
    if (shortName == null || !internalName.endsWith(shortName)) {
      return getClassName(internalName);
    }
    if (internalName.length() == shortName.length()) {
      return shortName;
    }
    if (parentName == null) {
      parentName = getClassName(internalName.substring(0, internalName.length() - shortName.length() - 1));
    }
    return parentName + '.' + shortName;
  }

  public static void newReferenceList(JavaClassReferenceListElementType type, StubElement parent, String[] types) {
    PsiReferenceList.Role role;

    if (type == JavaStubElementTypes.EXTENDS_LIST) role = PsiReferenceList.Role.EXTENDS_LIST;
    else if (type == JavaStubElementTypes.IMPLEMENTS_LIST) role = PsiReferenceList.Role.IMPLEMENTS_LIST;
    else if (type == JavaStubElementTypes.THROWS_LIST) role = PsiReferenceList.Role.THROWS_LIST;
    else if (type == JavaStubElementTypes.EXTENDS_BOUND_LIST) role = PsiReferenceList.Role.EXTENDS_BOUNDS_LIST;
    else throw new IllegalArgumentException("Unknown type: " + type);

    new PsiClassReferenceListStubImpl(type, parent, types, role);
  }

  @Nullable
  private static String parseClassDescription(String superName, String[] interfaces, List<String> convertedInterfaces) {
    String convertedSuper = superName != null ? getClassName(superName) : null;
    for (String anInterface : interfaces) {
      convertedInterfaces.add(getClassName(anInterface));
    }
    return convertedSuper;
  }

  @Nullable
  private static String parseClassSignature(CharacterIterator signatureIterator, List<String> convertedInterfaces) throws ClsFormatException {
    String convertedSuper = SignatureParsing.parseTopLevelClassRefSignature(signatureIterator);
    while (signatureIterator.current() != CharacterIterator.DONE) {
      String ifs = SignatureParsing.parseTopLevelClassRefSignature(signatureIterator);
      if (ifs == null) throw new ClsFormatException();
      convertedInterfaces.add(ifs);
    }
    return convertedSuper;
  }

  private static int packCommonFlags(int access) {
    int flags = 0;

    if ((access & Opcodes.ACC_PRIVATE) != 0) flags |= ModifierFlags.PRIVATE_MASK;
    else if ((access & Opcodes.ACC_PROTECTED) != 0) flags |= ModifierFlags.PROTECTED_MASK;
    else if ((access & Opcodes.ACC_PUBLIC) != 0) flags |= ModifierFlags.PUBLIC_MASK;
    else flags |= ModifierFlags.PACKAGE_LOCAL_MASK;

    if ((access & Opcodes.ACC_STATIC) != 0) flags |= ModifierFlags.STATIC_MASK;
    if ((access & Opcodes.ACC_FINAL) != 0) flags |= ModifierFlags.FINAL_MASK;

    return flags;
  }

  private static int packClassFlags(int access) {
    int flags = packCommonFlags(access);
    if ((access & Opcodes.ACC_ABSTRACT) != 0) flags |= ModifierFlags.ABSTRACT_MASK;
    return flags;
  }

  private static int packFieldFlags(int access) {
    int flags = packCommonFlags(access);
    if ((access & Opcodes.ACC_VOLATILE) != 0) flags |= ModifierFlags.VOLATILE_MASK;
    if ((access & Opcodes.ACC_TRANSIENT) != 0) flags |= ModifierFlags.TRANSIENT_MASK;
    return flags;
  }

  private static int packMethodFlags(int access, boolean isInterface) {
    int flags = packCommonFlags(access);

    if ((access & Opcodes.ACC_SYNCHRONIZED) != 0) flags |= ModifierFlags.SYNCHRONIZED_MASK;
    if ((access & Opcodes.ACC_NATIVE) != 0) flags |= ModifierFlags.NATIVE_MASK;
    if ((access & Opcodes.ACC_STRICT) != 0) flags |= ModifierFlags.STRICTFP_MASK;

    if ((access & Opcodes.ACC_ABSTRACT) != 0) flags |= ModifierFlags.ABSTRACT_MASK;
    else if (isInterface && (access & Opcodes.ACC_STATIC) == 0) flags |= ModifierFlags.DEFENDER_MASK;

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
    return new AnnotationTextCollector(desc, new Consumer<String>() {
      @Override
      public void consume(String text) {
        new PsiAnnotationStubImpl(myModList, text);
      }
    });
  }

  @Override
  public void visitInnerClass(String name, String outerName, String innerName, int access) {
    if ((access & Opcodes.ACC_SYNTHETIC) != 0) return;
    if (innerName == null || outerName == null) return;

    if ((getClassName(outerName) + '.' + innerName).equals(myResult.getQualifiedName()) && myParent instanceof PsiFileStub) {
      // our result is inner class
      throw new OutOfOrderInnerClassException();
    }

    if (myInternalName.equals(outerName)) {
      T innerClass = myInnersStrategy.findInnerClass(innerName, mySource);
      if (innerClass != null) {
        myInnersStrategy.accept(innerClass, new StubBuildingVisitor<T>(innerClass, myInnersStrategy, myResult, access, innerName));
      }
    }
  }

  @Override
  @Nullable
  public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
    if ((access & Opcodes.ACC_SYNTHETIC) != 0) return null;
    if (name == null) return null;

    byte flags = PsiFieldStubImpl.packFlags((access & Opcodes.ACC_ENUM) != 0, (access & Opcodes.ACC_DEPRECATED) != 0, false, false);
    TypeInfo type = fieldType(desc, signature);
    String initializer = constToString(value, type.text, false);
    PsiFieldStub stub = new PsiFieldStubImpl(myResult, name, type, initializer, flags);
    PsiModifierListStub modList = new PsiModifierListStubImpl(stub, packFieldFlags(access));
    return new AnnotationCollectingVisitor(modList);
  }

  @NotNull
  public static TypeInfo fieldType(String desc, String signature) {
    if (signature != null) {
      try {
        return TypeInfo.fromString(SignatureParsing.parseTypeString(new StringCharacterIterator(signature, 0)));
      }
      catch (ClsFormatException e) {
        return fieldTypeViaDescription(desc);
      }
    }
    else {
      return fieldTypeViaDescription(desc);
    }
  }

  @NotNull
  private static TypeInfo fieldTypeViaDescription(@NotNull String desc) {
    Type type = Type.getType(desc);
    int dim = type.getSort() == Type.ARRAY ? type.getDimensions() : 0;
    if (dim > 0) {
      type = type.getElementType();
    }
    return new TypeInfo(getTypeText(type), (byte)dim, false, PsiAnnotationStub.EMPTY_ARRAY);
  }

  private static final String[] parameterNames = {"p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9"};

  @Override
  @Nullable
  public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
    // JLS 13.1 says: Any constructs introduced by the compiler that do not have a corresponding construct in the source code
    // must be marked as synthetic, except for default constructors and the class initialization method.
    // However Scala compiler erroneously generates ACC_BRIDGE instead of ACC_SYNTHETIC flag for in-trait implementation delegation.
    // See IDEA-78649
    if ((access & Opcodes.ACC_SYNTHETIC) != 0) return null;

    if (SYNTHETIC_CLASS_INIT_METHOD.equals(name)) return null;

    // skip semi-synthetic enum methods
    boolean isEnum = myResult.isEnum();
    if (isEnum) {
      if ("values".equals(name) && desc.startsWith("()")) return null;
      if ("valueOf".equals(name) && desc.startsWith("(Ljava/lang/String;)")) return null;
    }

    boolean isDeprecated = (access & Opcodes.ACC_DEPRECATED) != 0;
    boolean isConstructor = SYNTHETIC_INIT_METHOD.equals(name);
    boolean isVarargs = (access & Opcodes.ACC_VARARGS) != 0;
    boolean isAnnotationMethod = myResult.isAnnotationType();

    if (!isConstructor && name == null) return null;

    byte flags = PsiMethodStubImpl.packFlags(isConstructor, isAnnotationMethod, isVarargs, isDeprecated, false, false);

    String canonicalMethodName = isConstructor ? myResult.getName() : name;
    List<String> args = new ArrayList<String>();
    List<String> throwables = exceptions != null ? new ArrayList<String>() : null;

    int modifiersMask = packMethodFlags(access, myResult.isInterface());
    PsiMethodStubImpl stub = new PsiMethodStubImpl(myResult, canonicalMethodName, flags, signature, args, throwables, desc, modifiersMask);

    PsiModifierListStub modList = (PsiModifierListStub)stub.findChildStubByType(JavaStubElementTypes.MODIFIER_LIST);
    assert modList != null : stub;

    if (isEnum && isConstructor && signature == null && args.size() >= 2 && JAVA_LANG_STRING.equals(args.get(0)) && "int".equals(args.get(1))) {
      // exclude synthetic enum constructor parameters
      args = args.subList(2, args.size());
    }

    boolean isNonStaticInnerClassConstructor =
      isConstructor && !(myParent instanceof PsiFileStub) && (myModList.getModifiersMask() & Opcodes.ACC_STATIC) == 0;
    boolean parsedViaGenericSignature = stub.isParsedViaGenericSignature();
    boolean shouldSkipFirstParamForNonStaticInnerClassConstructor = !parsedViaGenericSignature && isNonStaticInnerClassConstructor;

    PsiParameterListStubImpl parameterList = new PsiParameterListStubImpl(stub);
    int paramCount = args.size();
    PsiParameterStubImpl[] paramStubs = new PsiParameterStubImpl[paramCount];
    for (int i = 0; i < paramCount; i++) {
      if (shouldSkipFirstParamForNonStaticInnerClassConstructor && i == 0) continue;

      String arg = args.get(i);
      boolean isEllipsisParam = isVarargs && i == paramCount - 1;
      TypeInfo typeInfo = TypeInfo.fromString(arg, isEllipsisParam);

      String paramName = i < parameterNames.length ? parameterNames[i] : "p" + (i + 1);
      PsiParameterStubImpl parameterStub = new PsiParameterStubImpl(parameterList, paramName, typeInfo, isEllipsisParam);
      paramStubs[i] = parameterStub;
      new PsiModifierListStubImpl(parameterStub, 0);
    }

    String[] thrownTypes = buildThrowsList(exceptions, throwables, parsedViaGenericSignature);
    newReferenceList(JavaStubElementTypes.THROWS_LIST, stub, thrownTypes);

    int localVarIgnoreCount = (access & Opcodes.ACC_STATIC) != 0 ? 0 : isConstructor && isEnum ? 3 : 1;
    int paramIgnoreCount = isConstructor && isEnum ? 2 : isNonStaticInnerClassConstructor ? 1 : 0;
    return new AnnotationParamCollectingVisitor(stub, modList, localVarIgnoreCount, paramIgnoreCount, paramCount, paramStubs);
  }

  private static String[] buildThrowsList(String[] exceptions, List<String> throwables, boolean parsedViaGenericSignature) {
    if (exceptions == null) return ArrayUtil.EMPTY_STRING_ARRAY;

    if (parsedViaGenericSignature && throwables != null && exceptions.length > throwables.size()) {
      // There seem to be an inconsistency (or bug) in class format. For instance, java.lang.Class.forName() method has
      // signature equal to "(Ljava/lang/String;)Ljava/lang/Class<*>;" (i.e. no exceptions thrown) but exceptions actually not empty,
      // method throws ClassNotFoundException
      parsedViaGenericSignature = false;
    }

    if (parsedViaGenericSignature && throwables != null) {
      return ArrayUtil.toStringArray(throwables);
    }
    else {
      String[] converted = ArrayUtil.newStringArray(exceptions.length);
      for (int i = 0; i < converted.length; i++) {
        converted[i] = getClassName(exceptions[i]);
      }
      return converted;
    }
  }

  @NotNull
  public static String parseMethodViaDescription(@NotNull String desc, @NotNull PsiMethodStubImpl stub, @NotNull List<String> args) {
    String returnType = getTypeText(Type.getReturnType(desc));
    Type[] argTypes = Type.getArgumentTypes(desc);
    for (Type argType : argTypes) {
      args.add(getTypeText(argType));
    }
    new PsiTypeParameterListStubImpl(stub);
    return returnType;
  }

  @NotNull
  public static String parseMethodViaGenericSignature(@NotNull String signature,
                                                      @NotNull PsiMethodStubImpl stub,
                                                      @NotNull List<String> args,
                                                      @Nullable List<String> throwables) throws ClsFormatException {
    StringCharacterIterator iterator = new StringCharacterIterator(signature);
    SignatureParsing.parseTypeParametersDeclaration(iterator, stub);

    if (iterator.current() != '(') {
      throw new ClsFormatException();
    }
    iterator.next();

    while (iterator.current() != ')' && iterator.current() != CharacterIterator.DONE) {
      args.add(SignatureParsing.parseTypeString(iterator));
    }

    if (iterator.current() != ')') {
      throw new ClsFormatException();
    }
    iterator.next();

    String returnType = SignatureParsing.parseTypeString(iterator);

    while (iterator.current() == '^') {
      iterator.next();
      String exType = SignatureParsing.parseTypeString(iterator);
      if (throwables != null) {
        throwables.add(exType);
      }
    }

    return returnType;
  }


  private static class AnnotationTextCollector extends AnnotationVisitor {
    private final StringBuilder myBuilder = new StringBuilder();
    private final Consumer<String> myCallback;
    private boolean hasParams = false;
    private final String myDesc;

    public AnnotationTextCollector(@Nullable String desc, Consumer<String> callback) {
      super(ASM_API);
      myCallback = callback;

      myDesc = desc;
      if (desc != null) {
        myBuilder.append('@').append(getTypeText(Type.getType(desc)));
      }
    }

    @Override
    public void visit(String name, Object value) {
      valuePairPrefix(name);
      myBuilder.append(constToString(value, null, true));
    }

    @Override
    public void visitEnum(String name, String desc, String value) {
      valuePairPrefix(name);
      myBuilder.append(getTypeText(Type.getType(desc))).append('.').append(value);
    }

    private void valuePairPrefix(String name) {
      if (!hasParams) {
        hasParams = true;
        if (myDesc != null) {
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
      return new AnnotationTextCollector(desc, new Consumer<String>() {
        @Override
        public void consume(String text) {
          myBuilder.append(text);
        }
      });
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      valuePairPrefix(name);
      myBuilder.append('{');
      return new AnnotationTextCollector(null, new Consumer<String>() {
        @Override
        public void consume(String text) {
          myBuilder.append(text).append('}');
        }
      });
    }

    @Override
    public void visitEnd() {
      if (hasParams && myDesc != null) {
        myBuilder.append(')');
      }
      myCallback.consume(myBuilder.toString());
    }
  }

  private static class AnnotationCollectingVisitor extends FieldVisitor {
    private final PsiModifierListStub myModList;

    private AnnotationCollectingVisitor(PsiModifierListStub modList) {
      super(ASM_API);
      myModList = modList;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return new AnnotationTextCollector(desc, new Consumer<String>() {
        @Override
        public void consume(String text) {
          new PsiAnnotationStubImpl(myModList, text);
        }
      });
    }
  }

  private static class AnnotationParamCollectingVisitor extends MethodVisitor {
    private final PsiMethodStub myOwner;
    private final PsiModifierListStub myModList;
    private final int myIgnoreCount;
    private final int myParamIgnoreCount;
    private final int myParamCount;
    private final PsiParameterStubImpl[] myParamStubs;
    private int myUsedParamSize = 0;
    private int myUsedParamCount = 0;

    private AnnotationParamCollectingVisitor(@NotNull PsiMethodStub owner,
                                             @NotNull PsiModifierListStub modList,
                                             int ignoreCount,
                                             int paramIgnoreCount,
                                             int paramCount,
                                             @NotNull PsiParameterStubImpl[] paramStubs) {
      super(ASM_API);
      myOwner = owner;
      myModList = modList;
      myIgnoreCount = ignoreCount;
      myParamIgnoreCount = paramIgnoreCount;
      myParamCount = paramCount;
      myParamStubs = paramStubs;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return new AnnotationTextCollector(desc, new Consumer<String>() {
        @Override
        public void consume(String text) {
          new PsiAnnotationStubImpl(myModList, text);
        }
      });
    }

    @Override
    public AnnotationVisitor visitAnnotationDefault() {
      return new AnnotationTextCollector(null, new Consumer<String>() {
        @Override
        public void consume(String text) {
          ((PsiMethodStubImpl)myOwner).setDefaultValueText(text);
        }
      });
    }

    @Override
    public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
      if (index >= myIgnoreCount) {
        // long and double variables increase the index by 2, not by 1
        int paramIndex = (index - myIgnoreCount == myUsedParamSize) ? myUsedParamCount : index - myIgnoreCount;
        if (paramIndex >= myParamCount) return;

        if (ClsParsingUtil.isJavaIdentifier(name, LanguageLevel.HIGHEST)) {
          PsiParameterStubImpl parameterStub = myParamStubs[paramIndex];
          if (parameterStub != null) {
            parameterStub.setName(name);
          }
        }

        myUsedParamCount = paramIndex + 1;
        if ("D".equals(desc) || "J".equals(desc)) {
          myUsedParamSize += 2;
        }
        else {
          myUsedParamSize++;
        }
      }
    }

    @Override
    @Nullable
    public AnnotationVisitor visitParameterAnnotation(final int parameter, String desc, boolean visible) {
      if (parameter < myParamIgnoreCount) {
        return null;
      }
      return new AnnotationTextCollector(desc, new Consumer<String>() {
        @Override
        public void consume(String text) {
          new PsiAnnotationStubImpl(myOwner.findParameter(parameter - myParamIgnoreCount).getModList(), text);
        }
      });
    }
  }

  @Nullable
  private static String constToString(@Nullable Object value, @Nullable String type, boolean anno) {
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
      else if (Double.isNaN(d)) {
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
        buffer.append(constToString(Array.get(value, i), type, anno));
      }
      buffer.append('}');
      return buffer.toString();
    }

    if (anno && value instanceof Type) {
      return getTypeText((Type)value) + ".class";
    }

    return null;
  }

  private static String getClassName(String name) {
    return getTypeText(Type.getObjectType(name));
  }

  @NotNull
  private static String getTypeText(@NotNull Type type) {
    String raw = type.getClassName();
    // As the '$' char is a valid java identifier and is actively used by byte code generators, the problem is
    // which occurrences of this char should be replaced and which should not.
    // Heuristic: replace only those $ occurrences that are surrounded non-"$" chars
    //   (most likely generated by javac to separate inner or anonymous class name)
    //   Leading and trailing $ chars should be left unchanged.
    return raw.indexOf('$') >= 0 ? REGEX_PATTERN.matcher(raw).replaceAll("\\.") : raw;
  }
}