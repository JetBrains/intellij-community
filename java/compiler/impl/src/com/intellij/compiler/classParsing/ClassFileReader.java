/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * created at Jan 2, 2002
 * @author Jeka
 */
package com.intellij.compiler.classParsing;

import com.intellij.compiler.SymbolTable;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.util.ArrayUtil;
import com.intellij.util.cls.BytePointer;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.cls.ClsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ClassFileReader {
  private final File myFile;
  private byte[] myData;
  private int[] myConstantPoolOffsets = null; // the last offset points to the constant pool end

  private String myQualifiedName;
  private String myGenericSignature;
  private List<ReferenceInfo> myReferences;
  private List<FieldInfo> myFields;
  private List<MethodInfo> myMethods;
  private String mySourceFileName;
  private String mySuperClassName;
  private String[] mySuperInterfaces;
  private final SymbolTable mySymbolTable;
  private AnnotationConstantValue[] myRuntimeVisibleAnnotations;
  private AnnotationConstantValue[] myRuntimeInvisibleAnnotations;
  private static final String CONSTRUCTOR_NAME = "<init>";
  private boolean myParsingDone;

  public ClassFileReader(@NotNull File file, SymbolTable symbolTable, @Nullable final byte[] fileContent) {
    mySymbolTable = symbolTable;
    myFile = file;
    myData = fileContent;
  }

  public String getPath() {
    return myFile.getAbsolutePath();
  }

  public Collection<ReferenceInfo> getReferences() throws ClsFormatException {
    parseConstantPool();
    return myReferences;
  }

  public MethodInfo[] getMethods() throws ClsFormatException{
    parseMembers();
    return myMethods.toArray(new MethodInfo[myMethods.size()]);
  }

  public FieldInfo[] getFields() throws ClsFormatException{
    parseMembers();
    return myFields.toArray(new FieldInfo[myFields.size()]);
  }

  private void parseMembers() throws ClsFormatException {
    if (myParsingDone) {
      return;
    }
    initConstantPool();
    myMethods = new ArrayList<MethodInfo>();
    myFields = new ArrayList<FieldInfo>();
    BytePointer ptr = new BytePointer(getData(), getConstantPoolEnd());
    ptr.offset += 2; // access flags
    ptr.offset += 2; // this class
    ptr.offset += 2; // super class
    int count = ClsUtil.readU2(ptr); // interface count
    ptr.offset += 2 * count; // skip interface infos
    count = ClsUtil.readU2(ptr); // field count
    while (count-- > 0) {
      FieldInfo field = (FieldInfo)readMemberStructure(ptr, true);
      String name = getSymbol(field.getName());
      if (name.indexOf('$') < 0 && name.indexOf('<') < 0){ // skip synthetic fields
        myFields.add(field);
      }
    }
    count = ClsUtil.readU2(ptr); // method count
    while (count-- > 0) {
      MethodInfo method = (MethodInfo)readMemberStructure(ptr, false);
      String name = getSymbol(method.getName());
      if (name.indexOf('$') < 0 && name.indexOf('<') < 0) { // skip synthetic methods
        myMethods.add(method);
      }
      else
      if (CONSTRUCTOR_NAME.equals(name)) { // store constructors
        myMethods.add(method);
      }
    }

    final ClsAttributeTable attributeTable = readAttributes(ptr);
    mySourceFileName = attributeTable.sourceFile;
    myGenericSignature = attributeTable.genericSignature;
    myRuntimeVisibleAnnotations = attributeTable.runtimeVisibleAnnotations;
    myRuntimeInvisibleAnnotations = attributeTable.runtimeInvisibleAnnotations;
    myParsingDone = true;
  }

  private String getSymbol(final int id) throws ClsFormatException {
    try {
      return mySymbolTable.getSymbol(id);
    }
    catch (CacheCorruptedException e) {
      throw new ClsFormatException(e.getLocalizedMessage());
    }
  }

  private MemberInfo readMemberStructure(BytePointer ptr, boolean isField) throws ClsFormatException {
    int flags = ClsUtil.readU2(ptr);
    int nameIndex = ClsUtil.readU2(ptr);
    int descriptorIndex = ClsUtil.readU2(ptr);

    BytePointer p = new BytePointer(getData(), getOffsetInConstantPool(nameIndex));
    String name = ClsUtil.readUtf8Info(p);
    p.offset = getOffsetInConstantPool(descriptorIndex);
    String descriptor = ClsUtil.readUtf8Info(p);

    if (isField) {
      final ClsAttributeTable attributeTable = readAttributes(ptr);
      return new FieldInfo(
        getSymbolId(name),
        getSymbolId(descriptor),
        attributeTable.genericSignature != null? getSymbolId(attributeTable.genericSignature) : -1,
        flags,
        attributeTable.constantValue,
        attributeTable.runtimeVisibleAnnotations,
        attributeTable.runtimeInvisibleAnnotations
      );
    }
    else {
      final ClsAttributeTable attributeTable = readAttributes(ptr);
      int[] intExceptions = null;
      if (attributeTable.exceptions != null) {
        intExceptions = ArrayUtil.newIntArray(attributeTable.exceptions.length);
        for (int idx = 0; idx < intExceptions.length; idx++) {
          intExceptions[idx]  = getSymbolId(attributeTable.exceptions[idx]);
        }
      }
      return new MethodInfo(
        getSymbolId(name),
        getSymbolId(descriptor),
        attributeTable.genericSignature != null? getSymbolId(attributeTable.genericSignature) : -1,
        flags,
        intExceptions,
        CONSTRUCTOR_NAME.equals(name),
        attributeTable.runtimeVisibleAnnotations,
        attributeTable.runtimeInvisibleAnnotations,
        attributeTable.runtimeVisibleParameterAnnotations,
        attributeTable.runtimeInvisibleParameterAnnotations,
        attributeTable.annotationDefault
      );
    }
  }

  private int getSymbolId(final String symbol)  throws ClsFormatException{
    try {
      return mySymbolTable.getId(symbol);
    }
    catch (CacheCorruptedException e) {
      throw new ClsFormatException(e.getLocalizedMessage());
    }
  }

  public String getQualifiedName() throws ClsFormatException {
    if (myQualifiedName == null) {
      BytePointer ptr = new BytePointer(getData(), getConstantPoolEnd() + 2);
      ptr.offset = getOffsetInConstantPool(ClsUtil.readU2(ptr));
      int tag = ClsUtil.readU1(ptr);
      if (tag != ClsUtil.CONSTANT_Class){
        throw new ClsFormatException();
      }
      ptr.offset = getOffsetInConstantPool(ClsUtil.readU2(ptr));
      myQualifiedName = ClsUtil.readUtf8Info(ptr, '/', '.'); // keep '$' in the names
    }
    return myQualifiedName;
  }

  /**
   * @return fully qualified name of the class' superclass. In case there is no super return ""
   */
  public String getSuperClass() throws ClsFormatException {
    if (mySuperClassName == null) {
      BytePointer ptr = new BytePointer(getData(), getConstantPoolEnd() + 4);
      int index = ClsUtil.readU2(ptr);
      if (index == 0) {
        if (CommonClassNames.JAVA_LANG_OBJECT.equals(getQualifiedName())) {
          mySuperClassName = "";
        }
        else {
          throw new ClsFormatException();
        }
      }
      else {
        ptr.offset = getOffsetInConstantPool(index);
        mySuperClassName = readClassInfo(ptr); // keep '$' in the name for anonymous classes
        if (isInterface()) {
          if (!CommonClassNames.JAVA_LANG_OBJECT.equals(mySuperClassName)) {
            throw new ClsFormatException();
          }
        }
        /*
        else {
          if (!MakeUtil.isAnonymous(mySuperClassName)) {
            mySuperClassName = mySuperClassName.replace('$', '.');
          }
        }
        */
      }
    }
    return mySuperClassName;
  }

  public String[] getSuperInterfaces() throws ClsFormatException {
    if (mySuperInterfaces == null) {
      BytePointer ptr = new BytePointer(getData(), getConstantPoolEnd() + 6);
      int count = ClsUtil.readU2(ptr);
      mySuperInterfaces = ArrayUtil.newStringArray(count);
      BytePointer auxPtr = new BytePointer(ptr.bytes, 0);
      for (int idx = 0; idx < mySuperInterfaces.length; idx++) {
        auxPtr.offset = getOffsetInConstantPool(ClsUtil.readU2(ptr));
        mySuperInterfaces[idx] = readClassInfo(auxPtr);
      }
    }
    return mySuperInterfaces;
  }


  public String getSourceFileName() throws ClsFormatException {
    parseMembers();
    final String fName = mySourceFileName;
    return fName != null? fName : "";
  }

  public String getGenericSignature() throws ClsFormatException {
    parseMembers();
    final String genericSignature = myGenericSignature;
    return genericSignature != null && !genericSignature.isEmpty() ? genericSignature : null;
  }

  public AnnotationConstantValue[] getRuntimeVisibleAnnotations() throws ClsFormatException {
    parseMembers();
    final AnnotationConstantValue[] annotations = myRuntimeVisibleAnnotations;
    return annotations != null? annotations : AnnotationConstantValue.EMPTY_ARRAY;
  }

  public AnnotationConstantValue[] getRuntimeInvisibleAnnotations() throws ClsFormatException {
    parseMembers();
    final AnnotationConstantValue[] annotations = myRuntimeInvisibleAnnotations;
    return annotations != null? annotations : AnnotationConstantValue.EMPTY_ARRAY;
  }

  private boolean isInterface(){
    return (getAccessFlags() & ClsUtil.ACC_INTERFACE) != 0;
  }

// helper methods
  private void parseConstantPool() throws ClsFormatException {
    if (myReferences != null) {
      return;
    }
    myReferences = new ArrayList<ReferenceInfo>();
    initConstantPool();
    final BytePointer ptr = new BytePointer(getData(), 0);
    ConstantPoolIterator iterator = new ConstantPoolIterator(ptr);
    while (iterator.hasMoreEntries()) {
      final int tag = ClsUtil.readU1(ptr);
      if (tag == ClsUtil.CONSTANT_Fieldref || tag == ClsUtil.CONSTANT_Methodref || tag == ClsUtil.CONSTANT_InterfaceMethodref) {
        //ptr.offset -= 1; // position to the beginning of the structure
        MemberReferenceInfo refInfo = readRefStructure(tag, ptr);
        if (refInfo != null) {
          myReferences.add(refInfo);
        }
        /*
        String name = mySymbolTable.getSymbol(refInfo.getMemberInfo().getName());
        if (name.indexOf('$') < 0 && name.indexOf('<') < 0) { // skip refs to synthetic members
          myReferences.add(refInfo);
        }
        else if ("<init>".equals(name)) { // add instance initializers (constructors)
          myReferences.add(refInfo);
        }
        else {
          System.out.println("ReferenceInfo thrown out: " + mySymbolTable.getSymbol(refInfo.getClassName()) + "." + mySymbolTable.getSymbol(refInfo.getMemberInfo().getName()));
          ourWasteReferenceObjectsCounter += 1;
        }
        */
      }
      else if (tag == ClsUtil.CONSTANT_Class) {
        ptr.offset -= 1; // position to the beginning of the structure
        String className = readClassInfo(ptr);
        myReferences.add(new ReferenceInfo(getSymbolId(className)));
      }
      iterator.next();
    }
    //System.out.println("ourWasteReferenceObjectsCounter = " + ourWasteReferenceObjectsCounter);
  }

  private MemberReferenceInfo readRefStructure(int tag, BytePointer ptr) throws ClsFormatException {
    /*
    if (tag != ClsUtil.CONSTANT_Fieldref && tag != ClsUtil.CONSTANT_Methodref && tag != ClsUtil.CONSTANT_InterfaceMethodref) {
      throw new ClsFormatException();
    }
    */
    int classInfoIndex = ClsUtil.readU2(ptr);
    int nameTypeInfoIndex = ClsUtil.readU2(ptr);

    ptr.offset = getOffsetInConstantPool(classInfoIndex);
    if (ClsUtil.CONSTANT_Class != ClsUtil.readU1(ptr)) {
      throw new ClsFormatException();
    }
    ptr.offset = getOffsetInConstantPool(ClsUtil.readU2(ptr));
    String className = ClsUtil.readUtf8Info(ptr, '/', '.'); // keep '$' in names

    ptr.offset = getOffsetInConstantPool(nameTypeInfoIndex);
    if (ClsUtil.CONSTANT_NameAndType != ClsUtil.readU1(ptr)) {
      throw new ClsFormatException();
    }
    int memberNameIndex = ClsUtil.readU2(ptr);
    int descriptorIndex = ClsUtil.readU2(ptr);

    ptr.offset = getOffsetInConstantPool(memberNameIndex);
    String memberName = ClsUtil.readUtf8Info(ptr);

    if ((memberName.indexOf('$') >= 0 || memberName.indexOf('<') >= 0) && !CONSTRUCTOR_NAME.equals(memberName)) { // skip refs to synthetic members
      return null;
    }

    ptr.offset = getOffsetInConstantPool(descriptorIndex);
    String descriptor = ClsUtil.readUtf8Info(ptr);

    MemberInfo info = ClsUtil.CONSTANT_Fieldref == tag? new FieldInfo(getSymbolId(memberName), getSymbolId(descriptor)) : new MethodInfo(getSymbolId(memberName), getSymbolId(descriptor), CONSTRUCTOR_NAME.equals(memberName));
    return new MemberReferenceInfo(getSymbolId(className), info);
  }

  public int getAccessFlags(){
    try{
      int offset = getConstantPoolEnd();
      byte[] data = getData();
      if (offset + 2 > data.length){
        throw new ClsFormatException();
      }
      int b1 = data[offset++] & 0xFF;
      int b2 = data[offset++] & 0xFF;
      return (b1 << 8) + b2;
    }
    catch(ClsFormatException e){
      return 0;
    }
  }

  private byte[] getData(){
    if (myData == null) {
      try{
        myData = FileUtil.loadFileBytes(myFile);
      }
      catch(IOException e){
        myData = ArrayUtil.EMPTY_BYTE_ARRAY;
      }
    }
    return myData;
  }

  private int getOffsetInConstantPool(int index) throws ClsFormatException {
    initConstantPool();
    if (index < 1 || index >= myConstantPoolOffsets.length){
      throw new ClsFormatException();
    }
    return myConstantPoolOffsets[index - 1];
  }

  private int getConstantPoolEnd() throws ClsFormatException {
    initConstantPool();
    return myConstantPoolOffsets[myConstantPoolOffsets.length - 1];
  }

  private void initConstantPool() throws ClsFormatException {
    if (myConstantPoolOffsets == null){
      BytePointer ptr = new BytePointer(getData(), 0);
      ConstantPoolIterator iterator = new ConstantPoolIterator(ptr);
      myConstantPoolOffsets = new int[iterator.getEntryCount()];
      myConstantPoolOffsets[0] = iterator.getCurrentOffset();
      int index = 1;
      while (iterator.hasMoreEntries()) {
        int tag = ClsUtil.readU1(ptr);
        if (tag == ClsUtil.CONSTANT_Long || tag == ClsUtil.CONSTANT_Double) {
          myConstantPoolOffsets[index++] = ptr.offset + 8; // takes 2 entries!
        }
        iterator.next();
        myConstantPoolOffsets[index++] = iterator.getCurrentOffset();
      }
    }
  }

  private String readClassInfo(BytePointer ptr) throws ClsFormatException{
    final int tag = ClsUtil.readU1(ptr);
    if (tag != ClsUtil.CONSTANT_Class){
      throw new ClsFormatException(CompilerBundle.message("class.parsing.error.wrong.record.tag.expected.another", tag, ClsUtil.CONSTANT_Class));
    }
    int index = ClsUtil.readU2(ptr);
    return ClsUtil.readUtf8Info(new BytePointer(ptr.bytes, getOffsetInConstantPool(index)), '/', '.');
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private ClsAttributeTable readAttributes(BytePointer ptr) throws ClsFormatException {
    int count = ClsUtil.readU2(ptr); // attributeCount
    final ClsAttributeTable attributes = new ClsAttributeTable();
    while (count-- > 0) {
      final String attrName = readAttributeName(ptr);
      if ("Exceptions".equals(attrName)) {
        attributes.exceptions = readExceptions(ptr);
      }
      else if ("Signature".equals(attrName)) {
        attributes.genericSignature = readSignatureAttribute(ptr);
      }
      else if ("SourceFile".equals(attrName)) {
        attributes.sourceFile = readSourceFileAttribute(ptr);
      }
      else if ("ConstantValue".equals(attrName)){
        attributes.constantValue = readFieldConstantValue(ptr);
      }
      else if ("RuntimeVisibleAnnotations".equals(attrName)) {
        attributes.runtimeVisibleAnnotations = readAnnotations(ptr);
      }
      else if ("RuntimeInvisibleAnnotations".equals(attrName)) {
        attributes.runtimeInvisibleAnnotations = readAnnotations(ptr);
      }
      else if ("RuntimeVisibleParameterAnnotations".equals(attrName)) {
        attributes.runtimeVisibleParameterAnnotations = readParameterAnnotations(ptr);
      }
      else if ("RuntimeInvisibleParameterAnnotations".equals(attrName)) {
        attributes.runtimeInvisibleParameterAnnotations = readParameterAnnotations(ptr);
      }
      else if ("AnnotationDefault".equals(attrName)) {
        attributes.annotationDefault = readAnnotationMemberValue(new BytePointer(ptr.bytes, ptr.offset + 6));
      }
      gotoNextAttribute(ptr);
    }
    return attributes;
  }

  private String readAttributeName(BytePointer p) throws ClsFormatException {
    final BytePointer ptr = new BytePointer(p.bytes, p.offset);
    final int nameIndex = ClsUtil.readU2(ptr);
    ptr.offset = getOffsetInConstantPool(nameIndex);
    return ClsUtil.readUtf8Info(ptr);
  }

  private static void gotoNextAttribute(BytePointer ptr) throws ClsFormatException {
    ptr.offset += 2; // skip name index
    final int length = ClsUtil.readU4(ptr);    // important! Do not inline since ptr.offset is also changed inside ClsUtil.readU4() method
    ptr.offset += length;
  }

  /**
      Signature_attribute {
        u2 attribute_name_index;    (must be equal to "Signature")
        u4 attribute_length;        (must be equal to 2)
        u2 signature_index;
      }
   */
  private String readSignatureAttribute(BytePointer p) throws ClsFormatException {
    final BytePointer ptr = new BytePointer(p.bytes, p.offset + 2); // position to the length
    if (ClsUtil.readU4(ptr) != 2) {
      return null;
    }
    ptr.offset = getOffsetInConstantPool(ClsUtil.readU2(ptr));
    return ClsUtil.readUtf8Info(ptr);
  }

  private String[] readExceptions(BytePointer p) throws ClsFormatException{
    final BytePointer ptr = new BytePointer(p.bytes, p.offset + 6); // position to the count of exceptions
    int count = ClsUtil.readU2(ptr);
    final ArrayList<String> array = new ArrayList<String>(count);
    while (count-- > 0) {
      int idx = ClsUtil.readU2(ptr);
      if (idx != 0) {
        final String exceptionClass = readClassInfo(new BytePointer(ptr.bytes, getOffsetInConstantPool(idx)));
        array.add(exceptionClass);
      }
    }
    return ArrayUtil.toStringArray(array);
  }

  private String readSourceFileAttribute(BytePointer p) throws ClsFormatException {
    BytePointer ptr = new BytePointer(p.bytes, p.offset + 2); // position to the length
    if (ClsUtil.readU4(ptr) != 2) {
      return null;
    }
    ptr.offset = getOffsetInConstantPool(ClsUtil.readU2(ptr));
    String path = ClsUtil.readUtf8Info(ptr);
    // jdk version 1.3.0 puts full path to the source, but later versions store only short name
    final int slashIndex = path.lastIndexOf('/');
    if (slashIndex >= 0) {
      path = path.substring(slashIndex + 1, path.length());
    }
    return path;
  }

  private ConstantValue readFieldConstantValue(BytePointer p) throws ClsFormatException{
    final BytePointer ptr = new BytePointer(p.bytes, p.offset + 2);
    if (ClsUtil.readU4(ptr) != 2) {
      throw new ClsFormatException(); // attribute length must be 2
    }
    int valueIndex = ClsUtil.readU2(ptr);
    ptr.offset = getOffsetInConstantPool(valueIndex);
    return readConstant(ptr);
  }

  private ConstantValue readConstant(final BytePointer ptr) throws ClsFormatException {
    final int tag = ClsUtil.readU1(ptr);
    switch (tag) {
      case ClsUtil.CONSTANT_Integer :
        int value = ClsUtil.readU4(ptr);
        return new IntegerConstantValue(value);
      case ClsUtil.CONSTANT_Float:
        float floatValue = ClsUtil.readFloat(ptr);
        return new FloatConstantValue(floatValue);
      case ClsUtil.CONSTANT_Long :
        int high = ClsUtil.readU4(ptr);
        int low = ClsUtil.readU4(ptr);
        long v = ((long)high << 32) | (low & 0xFFFFFFFFL);
        return new LongConstantValue(v);
      case ClsUtil.CONSTANT_Double :
        double doubleValue = ClsUtil.readDouble(ptr);
        return new DoubleConstantValue(doubleValue);
      case ClsUtil.CONSTANT_String :
        int stringIndex = ClsUtil.readU2(ptr);
        ptr.offset = getOffsetInConstantPool(stringIndex);
        return new StringConstantValue(ClsUtil.readUtf8Info(ptr));
      default : throw new ClsFormatException();
    }
  }

  private AnnotationConstantValue[] readAnnotations(BytePointer p) throws ClsFormatException {
    final BytePointer ptr = new BytePointer(p.bytes, p.offset + 6);
    return readAnnotationsArray(ptr);
  }

  private AnnotationConstantValue[][] readParameterAnnotations(BytePointer p) throws ClsFormatException {
    final BytePointer ptr = new BytePointer(p.bytes, p.offset + 6); // position to the number of parameters
    final int numberOfParams = ClsUtil.readU1(ptr);
    if (numberOfParams == 0) {
      return null;
    }
    final AnnotationConstantValue[][] annotations = new AnnotationConstantValue[numberOfParams][];
    for (int parameterIndex = 0; parameterIndex < numberOfParams; parameterIndex++) {
      annotations[parameterIndex] = readAnnotationsArray(ptr);
    }
    return annotations;
  }

  private AnnotationConstantValue[] readAnnotationsArray(BytePointer ptr) throws ClsFormatException {
    final int numberOfAnnotations = ClsUtil.readU2(ptr);
    if (numberOfAnnotations == 0) {
      return AnnotationConstantValue.EMPTY_ARRAY;
    }
    AnnotationConstantValue[] annotations = new AnnotationConstantValue[numberOfAnnotations];
    for (int attributeIndex = 0; attributeIndex < numberOfAnnotations; attributeIndex++) {
      annotations[attributeIndex] = readAnnotation(ptr);
    }
    return annotations;
  }

  private AnnotationConstantValue readAnnotation(BytePointer ptr) throws ClsFormatException {
    final int classInfoIndex = ClsUtil.readU2(ptr);
    final String qName = readAnnotationClassName(new BytePointer(ptr.bytes, getOffsetInConstantPool(classInfoIndex)));
    final List<AnnotationNameValuePair> memberValues = new ArrayList<AnnotationNameValuePair>();
    final int numberOfPairs = ClsUtil.readU2(ptr);
    for (int idx = 0; idx < numberOfPairs; idx++) {
      final int memberNameIndex = ClsUtil.readU2(ptr);
      final String memberName = ClsUtil.readUtf8Info(ptr.bytes, getOffsetInConstantPool(memberNameIndex));
      final ConstantValue memberValue = readAnnotationMemberValue(ptr);
      memberValues.add(new AnnotationNameValuePair(getSymbolId(memberName), memberValue));
    }
    return new AnnotationConstantValue(getSymbolId(qName), memberValues.toArray(new AnnotationNameValuePair[memberValues.size()]));
  }

  private String readAnnotationClassName(BytePointer ptr) throws ClsFormatException {
    // TODO: need this method because because of incomplete class format spec at the moment of writing
    // it is not clear what structure is expected: CONSTANT_Utf8 or CONSTANT_Class
    final int tag = ClsUtil.readU1(ptr);
    if (tag == ClsUtil.CONSTANT_Utf8) {
      return ClsUtil.getTypeText(ptr.bytes, ptr.offset + 2); //skip length
    }
    if (tag == ClsUtil.CONSTANT_Class){
      ptr.offset -= 1; // rollback
      return readClassInfo(ptr);
    }
    //noinspection HardCodedStringLiteral
    throw new ClsFormatException(CompilerBundle.message("class.parsing.error.wrong.record.tag.expected.another", tag, "CONSTANT_Utf8(" + ClsUtil.CONSTANT_Utf8 + ") / CONSTANT_Class(" + ClsUtil.CONSTANT_Class + ")"));
  }

  private ConstantValue readAnnotationMemberValue(BytePointer ptr) throws ClsFormatException {
    final char tag = (char)ClsUtil.readU1(ptr);
    switch (tag) {
      case 'B':
      case 'C':
      case 'D':
      case 'F':
      case 'I':
      case 'J':
      case 'S':
      case 'Z': {
        final int valueIndex = ClsUtil.readU2(ptr);
        return new AnnotationPrimitiveConstantValue(tag, readConstant(new BytePointer(ptr.bytes, getOffsetInConstantPool(valueIndex))));
      }
      case 's': {
        final int valueIndex = ClsUtil.readU2(ptr);
        return new StringConstantValue(ClsUtil.readUtf8Info(ptr.bytes, getOffsetInConstantPool(valueIndex)));
      }
      case 'e': {
        final int typeNameIndex = ClsUtil.readU2(ptr);
        final int constantNameIndex = ClsUtil.readU2(ptr);
        final String typeName = ClsUtil.readUtf8Info(ptr.bytes, getOffsetInConstantPool(typeNameIndex));
        final String constantName = ClsUtil.readUtf8Info(ptr.bytes, getOffsetInConstantPool(constantNameIndex));
        return new EnumConstantValue(getSymbolId(typeName), getSymbolId(constantName));
      }
      case 'c' : {
        final int classInfoIndex = ClsUtil.readU2(ptr);
        BytePointer p = new BytePointer(ptr.bytes, getOffsetInConstantPool(classInfoIndex));
        final int recordTag = ClsUtil.readU1(p);
        if (recordTag != ClsUtil.CONSTANT_Utf8) {
          throw new ClsFormatException(CompilerBundle.message("class.parsing.error.wrong.record.tag.expected.another", recordTag, ClsUtil.CONSTANT_Utf8));
        }
        p.offset += 2; //Skip length
        final String className = ClsUtil.getTypeText(p.bytes, p.offset);
        return new ClassInfoConstantValue(getSymbolId(className));
      }
      case '@' : {
        return readAnnotation(ptr);
      }
      case '[' : {
        final int numberOfValues = ClsUtil.readU2(ptr);
        final ConstantValue[] values = new ConstantValue[numberOfValues];
        for (int idx = 0; idx < numberOfValues; idx++) {
          values[idx] = readAnnotationMemberValue(ptr);
        }
        return new ConstantValueArray(values);
      }
      default : throw new ClsFormatException(CompilerBundle.message("class.parsing.error.wrong.tag.annotation.member.value", tag));
    }
  }

  private static class ClsAttributeTable {
    public String[] exceptions;
    public String genericSignature;
    public String sourceFile;
    public ConstantValue constantValue;
    public AnnotationConstantValue[] runtimeVisibleAnnotations;
    public AnnotationConstantValue[] runtimeInvisibleAnnotations;
    public AnnotationConstantValue[][] runtimeVisibleParameterAnnotations;
    public AnnotationConstantValue[][] runtimeInvisibleParameterAnnotations;
    public ConstantValue annotationDefault;
  }

  private static class ConstantPoolIterator {
    private final BytePointer myPtr;
    private final int myEntryCount;
    private int myCurrentEntryIndex;
    private int myCurrentOffset;

    public ConstantPoolIterator(BytePointer ptr) throws ClsFormatException {
      myPtr = ptr;
      myPtr.offset = 0;
      int magic = ClsUtil.readU4(myPtr);
      if (magic != ClsUtil.MAGIC){
        throw new ClsFormatException();
      }
      myPtr.offset += 2; // minor version
      myPtr.offset += 2; // major version
      myEntryCount = ClsUtil.readU2(myPtr);
      if (myEntryCount < 1){
        throw new ClsFormatException();
      }
      myCurrentEntryIndex = 1; // Entry at index 0 is included in the count but is not present in the constant pool
      myCurrentOffset = myPtr.offset;
    }

    public int getEntryCount() {
      return myEntryCount;
    }

    public int getCurrentOffset() {
      return myCurrentOffset;
    }

    /**
     *  tests if there are unread entries
     */
    public boolean hasMoreEntries() {
      return myCurrentEntryIndex < myEntryCount;
    }

    /**
     * Positions the pointer to the next entry
     */
    public void next() throws ClsFormatException {
      myPtr.offset = myCurrentOffset;
      int tag = ClsUtil.readU1(myPtr);
      switch(tag){
        default:
          throw new ClsFormatException();

        case ClsUtil.CONSTANT_Class:
        case ClsUtil.CONSTANT_String:
          myPtr.offset += 2;
        break;

        case ClsUtil.CONSTANT_Fieldref:
        case ClsUtil.CONSTANT_Methodref:
        case ClsUtil.CONSTANT_InterfaceMethodref:
        case ClsUtil.CONSTANT_Integer:
        case ClsUtil.CONSTANT_Float:
        case ClsUtil.CONSTANT_NameAndType:
          myPtr.offset += 4;
        break;

        case ClsUtil.CONSTANT_Long:
        case ClsUtil.CONSTANT_Double:
          myPtr.offset += 8;
          myCurrentEntryIndex++; // takes 2 entries
        break;

        case ClsUtil.CONSTANT_Utf8:
          int length = ClsUtil.readU2(myPtr);
          myPtr.offset += length;
        break;

        case ClsUtil.CONSTANT_MethodHandle:
          myPtr.offset += 3;
          break;
        case ClsUtil.CONSTANT_MethodType:
          myPtr.offset += 2;
          break;
        case ClsUtil.CONSTANT_InvokeDynamic:
          myPtr.offset += 4;
          break;
      }
      myCurrentEntryIndex++;
      myCurrentOffset = myPtr.offset;
    }
  }
}
