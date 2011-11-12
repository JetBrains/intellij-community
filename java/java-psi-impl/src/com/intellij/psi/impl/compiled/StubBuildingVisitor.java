/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.impl.compiled;

import com.intellij.lexer.JavaLexer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.impl.cache.ModifierFlags;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.*;
import com.intellij.psi.impl.java.stubs.impl.*;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.EmptyVisitor;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class StubBuildingVisitor<T> implements ClassVisitor {
  private static final Pattern REGEX_PATTERN = Pattern.compile("(?<=[^\\$])\\${1}(?=[^\\$])");

  public static final String DOUBLE_POSITIVE_INF = "1.0 / 0.0";
  public static final String DOUBLE_NEGATIVE_INF = "-1.0 / 0.0";
  public static final String DOUBLE_NAN = "0.0d / 0.0";

  public static final String FLOAT_POSITIVE_INF = "1.0f / 0.0";
  public static final String FLOAT_NEGATIVE_INF = "-1.0f / 0.0";
  public static final String FLOAT_NAN = "0.0f / 0.0";


  private final InnerClassSourceStrategy<T> myInnersStrategy;
  private final StubElement myParent;
  private final int myAccess;
  private final T mySource;
  private PsiModifierListStub myModList;
  private PsiClassStub myResult;
  @NonNls private static final String SYNTHETIC_CLINIT_METHOD = "<clinit>";
  @NonNls private static final String SYNTHETIC_INIT_METHOD = "<init>";
  private JavaLexer myLexer;

  public StubBuildingVisitor(final T classSource, InnerClassSourceStrategy<T> innersStrategy, final StubElement parent, final int access) {
    mySource = classSource;
    myInnersStrategy = innersStrategy;
    myParent = parent;
    myAccess = access;
  }

  public PsiClassStub<?> getResult() {
    return myResult;
  }

  @Override
  public void visit(final int version,
                    final int access,
                    final String name,
                    final String signature,
                    final String superName,
                    final String[] interfaces) {
    String fqn = getClassName(name);

    final String shortName = PsiNameHelper.getShortClassName(fqn);

    final int flags = myAccess == 0 ? access : myAccess;

    boolean isDeprecated = (flags & Opcodes.ACC_DEPRECATED) != 0;
    boolean isInterface = (flags & Opcodes.ACC_INTERFACE) != 0;
    boolean isEnum = (flags & Opcodes.ACC_ENUM) != 0;
    boolean isAnnotationType = (flags & Opcodes.ACC_ANNOTATION) != 0;

    final byte stubFlags = PsiClassStubImpl.packFlags(isDeprecated, isInterface, isEnum, false, false, isAnnotationType, false, false);

    myResult = new PsiClassStubImpl(JavaStubElementTypes.CLASS, myParent, fqn, shortName, null, stubFlags);

    LanguageLevel languageLevel = convertFromVersion(version);
    myLexer = new JavaLexer(languageLevel);

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
    } else {
      new PsiTypeParameterListStubImpl(myResult);
    }

    String convertedSuper;
    List<String> convertedInterfaces = new ArrayList<String>();
    if (signatureIterator == null) {
      convertedSuper = parseClassDescription(superName, interfaces, convertedInterfaces);
    } else {
      try {
        convertedSuper = parseClassSignature(signatureIterator, convertedInterfaces);
      }
      catch (ClsFormatException e) {
        new PsiTypeParameterListStubImpl(myResult);
        convertedSuper = parseClassDescription(superName, interfaces, convertedInterfaces);
      }
    }

    String[] interfacesArray = ArrayUtil.toStringArray(convertedInterfaces);
    if (isInterface) {
      new PsiClassReferenceListStubImpl(JavaStubElementTypes.EXTENDS_LIST, myResult, interfacesArray, PsiReferenceList.Role.EXTENDS_LIST);
      new PsiClassReferenceListStubImpl(JavaStubElementTypes.IMPLEMENTS_LIST, myResult, ArrayUtil.EMPTY_STRING_ARRAY,
                                        PsiReferenceList.Role.IMPLEMENTS_LIST);
    } else {
      if (convertedSuper != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(convertedSuper)) {
        new PsiClassReferenceListStubImpl(JavaStubElementTypes.EXTENDS_LIST, myResult, new String[]{convertedSuper},
                                          PsiReferenceList.Role.EXTENDS_LIST);
      } else {
        new PsiClassReferenceListStubImpl(JavaStubElementTypes.EXTENDS_LIST, myResult, ArrayUtil.EMPTY_STRING_ARRAY, PsiReferenceList.Role.EXTENDS_LIST);
      }
      new PsiClassReferenceListStubImpl(JavaStubElementTypes.IMPLEMENTS_LIST, myResult, interfacesArray,
                                        PsiReferenceList.Role.IMPLEMENTS_LIST);
    }
  }

  @Nullable
  private static String parseClassDescription(final String superName, final String[] interfaces, final List<String> convertedInterfaces) {
    final String convertedSuper = superName != null ? getClassName(superName) : null;
    for (String anInterface : interfaces) {
      convertedInterfaces.add(getClassName(anInterface));
    }
    return convertedSuper;
  }

  @Nullable
  private static String parseClassSignature(final CharacterIterator signatureIterator, final List<String> convertedInterfaces)
    throws ClsFormatException {
    final String convertedSuper = SignatureParsing.parseToplevelClassRefSignature(signatureIterator);
    while (signatureIterator.current() != CharacterIterator.DONE) {
      final String ifs = SignatureParsing.parseToplevelClassRefSignature(signatureIterator);
      if (ifs == null) throw new ClsFormatException();

      convertedInterfaces.add(ifs);
    }
    return convertedSuper;
  }

  private static LanguageLevel convertFromVersion(final int version) {
    if (version == Opcodes.V1_1 || version == Opcodes.V1_2 || version == Opcodes.V1_3) {
      return LanguageLevel.JDK_1_3;
    }

    if (version == Opcodes.V1_4) {
      return LanguageLevel.JDK_1_4;
    }

    if (version == Opcodes.V1_5 || version == Opcodes.V1_6) {
      return LanguageLevel.JDK_1_5;
    }

    return LanguageLevel.HIGHEST;
  }

  private static int packCommonFlags(final int access) {
    int flags = 0;

    if ((access & Opcodes.ACC_PRIVATE) != 0) {
      flags |= ModifierFlags.PRIVATE_MASK;
    }
    else if ((access & Opcodes.ACC_PROTECTED) != 0) {
      flags |= ModifierFlags.PROTECTED_MASK;
    }
    else if ((access & Opcodes.ACC_PUBLIC) != 0) {
      flags |= ModifierFlags.PUBLIC_MASK;
    }
    else {
      flags |= ModifierFlags.PACKAGE_LOCAL_MASK;
    }

    if ((access & Opcodes.ACC_STATIC) != 0) {
      flags |= ModifierFlags.STATIC_MASK;
    }
    if ((access & Opcodes.ACC_FINAL) != 0) {
      flags |= ModifierFlags.FINAL_MASK;
    }

    return flags;
  }

  private static int packClassFlags(final int access) {
    int flags = packCommonFlags(access);

    if ((access & Opcodes.ACC_ABSTRACT) != 0) {
      flags |= ModifierFlags.ABSTRACT_MASK;
    }

    return flags;
  }

  private static int packFieldFlags(final int access) {
    int flags = packCommonFlags(access);

    if ((access & Opcodes.ACC_VOLATILE) != 0) {
      flags |= ModifierFlags.VOLATILE_MASK;
    }
    if ((access & Opcodes.ACC_TRANSIENT) != 0) {
      flags |= ModifierFlags.TRANSIENT_MASK;
    }

    return flags;
  }

  private static int packMethodFlags(final int access) {
    int flags = packCommonFlags(access);

    if ((access & Opcodes.ACC_SYNCHRONIZED) != 0) {
      flags |= ModifierFlags.SYNCHRONIZED_MASK;
    }
    if ((access & Opcodes.ACC_NATIVE) != 0) {
      flags |= ModifierFlags.NATIVE_MASK;
    }
    if ((access & Opcodes.ACC_ABSTRACT) != 0) {
      flags |= ModifierFlags.ABSTRACT_MASK;
    }
    if ((access & Opcodes.ACC_STRICT) != 0) {
      flags |= ModifierFlags.STRICTFP_MASK;
    }

    return flags;
  }

  @Override
  public void visitSource(final String source, final String debug) {
    ((PsiClassStubImpl)myResult).setSourceFileName(source);
  }

  @Override
  public void visitOuterClass(final String owner, final String name, final String desc) {
  }

  @Override
  public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
    return new AnnotationTextCollector(desc, new AnnotationResultCallback() {
      @Override
      public void callback(final String text) {
        new PsiAnnotationStubImpl(myModList, text);
      }
    });
  }

  @Override
  public void visitAttribute(final Attribute attr) {
  }

  @Override
  public void visitInnerClass(final String name, final String outerName, final String innerName, final int access) {
    if ((access & Opcodes.ACC_SYNTHETIC) != 0) return;
    if (!isCorrectName(innerName)) return;

    if (innerName == null || outerName == null || !getClassName(outerName).equals(myResult.getQualifiedName())) return;

    final T innerSource = myInnersStrategy.findInnerClass(innerName, mySource);
    if (innerSource == null) return;

    final ClassReader reader = myInnersStrategy.readerForInnerClass(innerSource);
    if (reader == null) return;

    final StubBuildingVisitor<T> classVisitor = new StubBuildingVisitor<T>(innerSource, myInnersStrategy, myResult, access);
    reader.accept(classVisitor, 0);
  }

  private boolean isCorrectName(String name) {
    if (name == null) return false;

    myLexer.start(name);
    if (myLexer.getTokenType() != JavaTokenType.IDENTIFIER) return false;
    myLexer.advance();
    return myLexer.getTokenType() == null;
  }

  @Override
  @Nullable
  public FieldVisitor visitField(final int access, final String name, final String desc, final String signature, final Object value) {
    if ((access & Opcodes.ACC_SYNTHETIC) != 0) return null;
    if (!isCorrectName(name)) return null;

    final byte flags = PsiFieldStubImpl.packFlags((access & Opcodes.ACC_ENUM) != 0, (access & Opcodes.ACC_DEPRECATED) != 0, false);
    PsiFieldStub stub = new PsiFieldStubImpl(myResult, name, fieldType(desc, signature), constToString(value), flags);
    final PsiModifierListStub modList = new PsiModifierListStubImpl(stub, packFieldFlags(access));
    return new AnnotationCollectingVisitor(stub, modList);
  }

  @NotNull
  private static TypeInfo fieldType(String desc, String signature) {
    if (signature != null) {
      try {
        return TypeInfo.fromString(SignatureParsing.parseTypeString(new StringCharacterIterator(signature, 0)));
      }
      catch (ClsFormatException e) {
        return fieldTypeViaDescription(desc);
      }
    } else {
      return fieldTypeViaDescription(desc);
    }
  }

  @NotNull
  private static TypeInfo fieldTypeViaDescription(final String desc) {
    Type type = Type.getType(desc);
    final int dim = type.getSort() == Type.ARRAY ? type.getDimensions() : 0;
    if (dim > 0) {
      type = type.getElementType();
    }
    return new TypeInfo(StringRef.fromString(getTypeText(type)), (byte)dim, false, Collections.<PsiAnnotationStub>emptyList()); //todo read annos from .class file
  }

  @Override
  @Nullable
  public MethodVisitor visitMethod(final int access,
                                   final String name,
                                   final String desc,
                                   final String signature,
                                   final String[] exceptions) {
    if ((access & Opcodes.ACC_SYNTHETIC) != 0) return null;
    if ((access & Opcodes.ACC_BRIDGE) != 0) return null;
    if (SYNTHETIC_CLINIT_METHOD.equals(name)) return null;

    boolean isDeprecated = (access & Opcodes.ACC_DEPRECATED) != 0;
    boolean isConstructor = SYNTHETIC_INIT_METHOD.equals(name);
    boolean isVarargs = (access & Opcodes.ACC_VARARGS) != 0;
    boolean isAnnotationMethod = myResult.isAnnotationType();

    if (!isConstructor && !isCorrectName(name)) return null;

    final byte flags = PsiMethodStubImpl.packFlags(isConstructor, isAnnotationMethod, isVarargs, isDeprecated, false);

    String canonicalMethodName = isConstructor ? myResult.getName() : name;
    final List<String> args = new ArrayList<String>();
    final List<String> throwables = exceptions != null ? new ArrayList<String>() : null;

    PsiMethodStubImpl stub = new PsiMethodStubImpl(myResult, StringRef.fromString(canonicalMethodName), flags, null);

    final PsiModifierListStub modList = new PsiModifierListStubImpl(stub, packMethodFlags(access));
    boolean parsedViaGenericSignature = false;
    String returnType;
    if (signature == null) {
      returnType = parseMethodViaDescription(desc, stub, args);
    }
    else {
      try {
        returnType = parseMethodViaGenericSignature(signature, stub, args, throwables);
        parsedViaGenericSignature = true;
      }
      catch (ClsFormatException e) {
        returnType = parseMethodViaDescription(desc, stub, args);
      }
    }

    stub.setReturnType(TypeInfo.fromString(returnType));


    final boolean isNonStaticInnerClassConstructor =
      isConstructor && !(myParent instanceof PsiFileStub) && (myModList.getModifiersMask() & Opcodes.ACC_STATIC) == 0;
    final boolean shouldSkipFirstParamForNonStaticInnerClassConstructor = !parsedViaGenericSignature && isNonStaticInnerClassConstructor;

    final PsiParameterListStubImpl parameterList = new PsiParameterListStubImpl(stub);
    final int paramCount = args.size();
    final PsiParameterStubImpl[] paramStubs = new PsiParameterStubImpl[paramCount];
    for (int i = 0; i < paramCount; i++) {
      if (shouldSkipFirstParamForNonStaticInnerClassConstructor && i == 0) continue;

      String arg = args.get(i);
      boolean isEllipsisParam = isVarargs && i == paramCount - 1;
      final TypeInfo typeInfo = TypeInfo.fromString(arg, isEllipsisParam);

      PsiParameterStubImpl parameterStub = new PsiParameterStubImpl(parameterList, "p" + (i + 1), typeInfo, isEllipsisParam);
      paramStubs [i] = parameterStub;
      new PsiModifierListStubImpl(parameterStub, 0);
    }

    String[] thrownTypes = buildThrowsList(exceptions, throwables, parsedViaGenericSignature);
    new PsiClassReferenceListStubImpl(JavaStubElementTypes.THROWS_LIST, stub, thrownTypes, PsiReferenceList.Role.THROWS_LIST);

    final boolean isEnumConstructor = isConstructor && myResult.isEnum();

    int localVarIgnoreCount = (access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
    if (isEnumConstructor) {
      localVarIgnoreCount += 2;
    }
    final int paramIgnoreCount = isEnumConstructor? 2 : isNonStaticInnerClassConstructor ? 1 : 0;
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

  private static String parseMethodViaDescription(final String desc, final PsiMethodStubImpl stub, final List<String> args) {
    final String returnType = getTypeText(Type.getReturnType(desc));
    final Type[] argTypes = Type.getArgumentTypes(desc);
    for (Type argType : argTypes) {
      args.add(getTypeText(argType));
    }
    new PsiTypeParameterListStubImpl(stub);
    return returnType;
  }

  private static String parseMethodViaGenericSignature(final String signature,
                                                       final PsiMethodStubImpl stub,
                                                       final List<String> args,
                                                       final List<String> throwables)
    throws ClsFormatException {
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
      throwables.add(SignatureParsing.parseTypeString(iterator));
    }

    return returnType;
  }

  @Override
  public void visitEnd() {
  }
  
  private static class AnnotationTextCollector implements AnnotationVisitor {
    private final StringBuilder myBuilder = new StringBuilder();
    private final AnnotationResultCallback myCallback;
    private boolean hasParams = false;
    private final String myDesc;

    public AnnotationTextCollector(@Nullable String desc, AnnotationResultCallback callback) {
      myCallback = callback;

      myDesc = desc;
      if (desc != null) {
        myBuilder.append('@').append(getTypeText(Type.getType(desc)));
      }
    }

    @Override
    public void visit(final String name, final Object value) {
      valuePairPrefix(name);
      myBuilder.append(constToString(value));
    }

    @Override
    public void visitEnum(final String name, final String desc, final String value) {
      valuePairPrefix(name);
      myBuilder.append(getTypeText(Type.getType(desc))).append(".").append(value);
    }

    private void valuePairPrefix(final String name) {
      if (!hasParams) {
        hasParams = true;
        if (myDesc != null) {
          myBuilder.append('(');
        }
      } else {
        myBuilder.append(',');
      }

      if (name != null && !"value".equals(name)) {
        myBuilder.append(name).append('=');
      }
    }

    @Override
    public AnnotationVisitor visitAnnotation(final String name, final String desc) {
      valuePairPrefix(name);
      return new AnnotationTextCollector(desc, new AnnotationResultCallback() {
        @Override
        public void callback(final String text) {
          myBuilder.append(text);
        }
      });
    }

    @Override
    public AnnotationVisitor visitArray(final String name) {
      valuePairPrefix(name);
      myBuilder.append("{");
      return new AnnotationTextCollector(null, new AnnotationResultCallback() {
        @Override
        public void callback(final String text) {
          myBuilder.append(text).append('}');
        }
      });
    }

    @Override
    public void visitEnd() {
      if (hasParams && myDesc != null) {
        myBuilder.append(')');
      }
      myCallback.callback(myBuilder.toString());
    }
  }

  private static class AnnotationCollectingVisitor extends EmptyVisitor {
    private final StubElement myOwner;
    private final PsiModifierListStub myModList;

    private AnnotationCollectingVisitor(final StubElement owner, final PsiModifierListStub modList) {
      myOwner = owner;
      myModList = modList;
    }

    @Override
    public AnnotationVisitor visitAnnotationDefault() {
      return new AnnotationTextCollector(null, new AnnotationResultCallback() {
        @Override
        public void callback(final String text) {
          ((PsiMethodStubImpl)myOwner).setDefaultValueText(text);
        }
      });
    }

    @Override
    public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
      return new AnnotationTextCollector(desc, new AnnotationResultCallback() {
        @Override
        public void callback(final String text) {
          new PsiAnnotationStubImpl(myModList, text);
        }
      });
    }

    @Override
    @Nullable
    public AnnotationVisitor visitParameterAnnotation(final int parameter, final String desc, final boolean visible) {
      return new AnnotationTextCollector(desc, new AnnotationResultCallback() {
        @Override
        public void callback(final String text) {
          new PsiAnnotationStubImpl(((PsiMethodStub)myOwner).findParameter(parameter).getModList(), text);
        }
      });
    }
  }

  private static class AnnotationParamCollectingVisitor extends AnnotationCollectingVisitor {
    private final int myIgnoreCount;
    private final int myParamIgnoreCount;
    private final int myParamCount;
    private final PsiParameterStubImpl[] myParamStubs;
    private int myUsedParamSize = 0;
    private int myUsedParamCount = 0;

    private AnnotationParamCollectingVisitor(final PsiMethodStub owner,
                                             final PsiModifierListStub modList,
                                             int ignoreCount,
                                             int paramIgnoreCount,
                                             int paramCount,
                                             PsiParameterStubImpl[] paramStubs) {
      super(owner, modList);
      myIgnoreCount = ignoreCount;
      myParamIgnoreCount = paramIgnoreCount;
      myParamCount = paramCount;
      myParamStubs = paramStubs;
    }

    @Override
    public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
      if (index >= myIgnoreCount) {
        // long and double variables increase the index by 2, not by 1
        int paramIndex = (index - myIgnoreCount == myUsedParamSize) ? myUsedParamCount : index - myIgnoreCount;
        if (paramIndex >= myParamCount) return;
        PsiParameterStubImpl parameterStub = myParamStubs[paramIndex];
        if (parameterStub != null) {
          parameterStub.setName(name);
        }
        myUsedParamCount = paramIndex+1;
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
      return (parameter < myParamIgnoreCount) ? null : super.visitParameterAnnotation(parameter - myParamIgnoreCount, desc, visible);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  @Nullable
  private static String constToString(final Object value) {
    if (value == null) return null;

    if (value instanceof String) return "\"" + StringUtil.escapeStringCharacters((String)value) + "\"";
    if (value instanceof Integer || value instanceof Boolean) return value.toString();
    if (value instanceof Long) return value.toString() + "L";

    if (value instanceof Double) {
      final double d = ((Double)value).doubleValue();
      if (Double.isInfinite(d)) {
        return d > 0 ? DOUBLE_POSITIVE_INF : DOUBLE_NEGATIVE_INF;
      }
      else if (Double.isNaN(d)) {
        return DOUBLE_NAN;
      }
      return Double.toString(d);
    }

    if (value instanceof Float) {
      final float v = ((Float)value).floatValue();

      if (Float.isInfinite(v)) {
        return v > 0 ? FLOAT_POSITIVE_INF : FLOAT_NEGATIVE_INF;
      }
      else if (Float.isNaN(v)) {
        return FLOAT_NAN;
      }
      else {
        return Float.toString(v) + "f";
      }
    }

    return null;
  }

  private interface AnnotationResultCallback {
    void callback(String text);
  }

  private static String getClassName(final String name) {
    return getTypeText(Type.getObjectType(name));
  }

  private static String getTypeText(final Type type) {
    final String raw = type.getClassName();
    // As the '$' char is a valid java identifier and is actively used by byte code generators, the problem is
    // which occurrences of this char should be replaced and which should not.
    // Heuristic: replace only those $ occurrences that are surrounded non-"$" chars
    //   (most likely generated by javac to separate inner or anonymous class name)
    //   Leading and trailing $ chars should be left unchanged.
    return raw.contains("$")? REGEX_PATTERN.matcher(raw).replaceAll("\\.") : raw;
  }
}
