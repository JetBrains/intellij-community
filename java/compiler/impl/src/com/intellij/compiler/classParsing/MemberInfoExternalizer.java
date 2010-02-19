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
 * created at Jan 10, 2002
 * @author Jeka
 */
package com.intellij.compiler.classParsing;

import com.intellij.openapi.diagnostic.Logger;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class MemberInfoExternalizer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.classParsing.MemberInfoExternalizer");

  public static final byte FIELD_INFO_TAG = 1;
  public static final byte METHOD_INFO_TAG = 2;

  public static final byte DECLARATION_INFO_TAG = 9;

  public static final byte REFERENCE_INFO_TAG = 11;
  public static final byte MEMBER_REFERENCE_INFO_TAG = 12;

  public static final byte LONG_CONSTANT_TAG = 3;
  public static final byte FLOAT_CONSTANT_TAG = 4;
  public static final byte DOUBLE_CONSTANT_TAG = 5;
  public static final byte INTEGER_CONSTANT_TAG = 6;
  public static final byte STRING_CONSTANT_TAG = 7;
  public static final byte CONSTANT_TAG = 8;
  public static final byte ANNOTATION_CONSTANT_TAG = 13;
  public static final byte ANNOTATION_PRIMITIVE_CONSTANT_TAG = 14;
  public static final byte CONSTANT_VALUE_ARRAY_TAG = 15;
  public static final byte CLASS_CONSTANT_VALUE_TAG = 16;
  public static final byte ENUM_CONSTANT_VALUE_TAG = 17;

  public static MemberInfo loadMemberInfo(DataInput in) throws IOException {
    byte tag = in.readByte();
    if (tag == METHOD_INFO_TAG) {
      return new MethodInfo(in);
    }
    else if (tag == FIELD_INFO_TAG) {
      return new FieldInfo(in);
    }
    LOG.error("Unknown member info");
    return null;
  }

  public static ReferenceInfo loadReferenceInfo(DataInput in) throws IOException {
    final byte tag = in.readByte();
    if (tag == REFERENCE_INFO_TAG) {
      return new ReferenceInfo(in);
    }
    else if (tag == MEMBER_REFERENCE_INFO_TAG) {
      return new MemberReferenceInfo(in);
    }
    LOG.error("Unknown declaration info tag: " + tag);
    return null;
  }

  public static ConstantValue loadConstantValue(DataInput in) throws IOException {
    final byte tag = in.readByte();
    if (tag == LONG_CONSTANT_TAG) {
      return new LongConstantValue(in);
    }
    else if (tag == FLOAT_CONSTANT_TAG) {
      return new FloatConstantValue(in);
    }
    else if (tag == DOUBLE_CONSTANT_TAG) {
      return new DoubleConstantValue(in);
    }
    else if (tag == INTEGER_CONSTANT_TAG) {
      return new IntegerConstantValue(in);
    }
    else if (tag == STRING_CONSTANT_TAG) {
      return new StringConstantValue(in);
    }
    else if (tag == CONSTANT_TAG) {
      return ConstantValue.EMPTY_CONSTANT_VALUE;
    }
    else if (tag == ANNOTATION_CONSTANT_TAG) {
      return new AnnotationConstantValue(in);
    }
    else if (tag == ANNOTATION_PRIMITIVE_CONSTANT_TAG) {
      return new AnnotationPrimitiveConstantValue(in);
    }
    else if (tag == CONSTANT_VALUE_ARRAY_TAG) {
      return new ConstantValueArray(in);
    }
    else if (tag == CLASS_CONSTANT_VALUE_TAG) {
      return new ClassInfoConstantValue(in);
    }
    else if (tag == ENUM_CONSTANT_VALUE_TAG) {
      return new EnumConstantValue(in);
    }
    LOG.error("Unknown constant value type " + tag);
    return null;
  }

  public static void saveMemberInfo(DataOutput out, MemberInfo info) throws IOException {
    if (info instanceof MethodInfo) {
      out.writeByte(METHOD_INFO_TAG);
    }
    else if (info instanceof FieldInfo){
      out.writeByte(FIELD_INFO_TAG);
    }
    else {
      LOG.error("Unknown member info");
    }
    info.save(out);
  }

  public static void saveReferenceInfo(DataOutput out, ReferenceInfo info) throws IOException {
    if (info instanceof MemberReferenceInfo) {
      out.writeByte(MEMBER_REFERENCE_INFO_TAG);
    }
    else{
      out.writeByte(REFERENCE_INFO_TAG);
    }
    info.save(out);
  }

  public static void saveConstantValue(DataOutput out, ConstantValue value) throws IOException {
    if (value instanceof LongConstantValue) {
      out.writeByte(LONG_CONSTANT_TAG);
    }
    else if (value instanceof FloatConstantValue){
      out.writeByte(FLOAT_CONSTANT_TAG);
    }
    else if (value instanceof DoubleConstantValue){
      out.writeByte(DOUBLE_CONSTANT_TAG);
    }
    else if (value instanceof IntegerConstantValue){
      out.writeByte(INTEGER_CONSTANT_TAG);
    }
    else if (value instanceof StringConstantValue){
      out.writeByte(STRING_CONSTANT_TAG);
    }
    else if (value instanceof AnnotationConstantValue) {
      out.writeByte(ANNOTATION_CONSTANT_TAG);
    }
    else if (value instanceof AnnotationPrimitiveConstantValue) {
      out.writeByte(ANNOTATION_PRIMITIVE_CONSTANT_TAG);
    }
    else if (value instanceof ConstantValueArray) {
      out.writeByte(CONSTANT_VALUE_ARRAY_TAG);
    }
    else if (value instanceof ClassInfoConstantValue) {
      out.writeByte(CLASS_CONSTANT_VALUE_TAG);
    }
    else if (value instanceof EnumConstantValue) {
      out.writeByte(ENUM_CONSTANT_VALUE_TAG);
    }
    else {
      out.writeByte(CONSTANT_TAG);
    }
    if (value != null) {
      value.save(out);
    }
  }

  public static AnnotationConstantValue[] readAnnotationConstantValueArray1(DataInput in) throws IOException {
    final int size = in.readInt();
    final AnnotationConstantValue[] array = size > 0? new AnnotationConstantValue[size] : AnnotationConstantValue.EMPTY_ARRAY;
    for (int idx = 0; idx < size; idx++) {
      array[idx] = (AnnotationConstantValue)loadConstantValue(in);
    }
    return array;
  }

  public static AnnotationConstantValue[][] readAnnotationConstantValueArray2(DataInput in) throws IOException {
    final int size = in.readInt();
    final AnnotationConstantValue[][] array = size > 0? new AnnotationConstantValue[size][] : AnnotationConstantValue.EMPTY_ARRAY_ARRAY;
    for (int idx = 0; idx < size; idx++) {
      array[idx] = readAnnotationConstantValueArray1(in);
    }
    return array;
  }

  public static void writeConstantValueArray1(DataOutput writer, final ConstantValue[] array) throws IOException {
    if (array != null && array.length > 0) {
      writer.writeInt(array.length);
      for (ConstantValue value : array) {
        saveConstantValue(writer, value);
      }
    }
    else {
      writer.writeInt(0);
    }
  }

  public static void writeConstantValueArray2(DataOutput writer, final ConstantValue[][] array) throws IOException {
    if (array != null && array.length > 0) {
      writer.writeInt(array.length);
      for (ConstantValue[] aArray : array) {
        writeConstantValueArray1(writer, aArray);
      }
    }
    else {
      writer.writeInt(0);
    }
  }

}
