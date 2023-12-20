// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.serializer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.dependency.java.*;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public final class JvmClassTestUtil {
  private static final String ANNOTATION1_NAME = "annotation1";
  private static final String ANNOTATION2_NAME = "annotation2";
  private static final int JVM_FLAGS_VALUE = 1234;

  private static final ParamAnnotation PARAM_ANNOTATION1 = new ParamAnnotation(23, new TypeRepr.ClassType("jvmName1"));
  private static final ParamAnnotation PARAM_ANNOTATION2 = new ParamAnnotation(32, new TypeRepr.ClassType("jvmName2"));

  private static final String SIGNATURE = "signature";
  private static final String SIGNATURE2 = "signature2";
  private static final String NAME = "name";
  private static final String NAME2 = "name2";
  private static final String DESCRIPTOR = "()V";
  private static final String DESCRIPTOR2 = "()V";
  private static final String EXCEPTION1 = "exception1";
  private static final String EXCEPTION2 = "exception2";
  private static final String EXCEPTION3 = "exception3";
  private static final String EXCEPTION4 = "exception4";
  private static final String FQ_NAME = "fqName";
  private static final String OUT_FILE_PATH = "outFilePath";
  private static final String SUPER_FQ_NAME = "superFqName";
  private static final String OUTER_FQ_NAME = "outerFqName";
  private static final String INTERFACE_1 = "Interface 1";
  private static final String INTERFACE_2 = "Interface 2";
  private static final String CLASS_TYPE_1 = "classType1";
  private static final String CLASS_TYPE_2 = "classType2";
  private static final String CLASS_NEW_USAGE = "ClassNewUsage";
  private static final Integer FIRST_JVM_FIELD_VALUE = 23;
  private static final String SECOND_JVM_FIELD_VALUE = "ClassNewUsage";
  private static final Long FIRST_JVM_METHOD_VALUE = 32L;
  private static final Float SECOND_JVM_METHOD_VALUE = 0F;

  @NotNull
  public static JvmClass createJvmClassNode() {
    TypeRepr.ClassType annotation1 = new TypeRepr.ClassType(ANNOTATION1_NAME);
    TypeRepr.ClassType annotation2 = new TypeRepr.ClassType(ANNOTATION2_NAME);
    Iterable<TypeRepr.ClassType> annotations = Arrays.asList(annotation1, annotation2);

    JVMFlags jvmFlags = new JVMFlags(JVM_FLAGS_VALUE);

    JvmField firstJvmField = new JvmField(jvmFlags, SIGNATURE, NAME, DESCRIPTOR, annotations, FIRST_JVM_FIELD_VALUE);
    JvmField secondJvmField = new JvmField(jvmFlags, SIGNATURE2, NAME2, DESCRIPTOR2, annotations, SECOND_JVM_FIELD_VALUE);
    Iterable<JvmField> fields = Arrays.asList(firstJvmField, secondJvmField);

    Set<ParamAnnotation> paramAnnotations = new HashSet<>(Arrays.asList(PARAM_ANNOTATION1, PARAM_ANNOTATION2));

    JvmMethod jvmMethod1 = new JvmMethod(jvmFlags, SIGNATURE, NAME, DESCRIPTOR, annotations, paramAnnotations, List.of(EXCEPTION1, EXCEPTION2), FIRST_JVM_METHOD_VALUE);
    JvmMethod jvmMethod2 = new JvmMethod(jvmFlags, SIGNATURE2, NAME2, DESCRIPTOR2, annotations, paramAnnotations, List.of(EXCEPTION3, EXCEPTION4), SECOND_JVM_METHOD_VALUE);
    Iterable<JvmMethod> methods = List.of(jvmMethod1, jvmMethod2);

    Iterable<TypeRepr.ClassType> classAnnotations =
      Arrays.asList(new TypeRepr.ClassType(CLASS_TYPE_1), new TypeRepr.ClassType(CLASS_TYPE_2));
    Iterable<Usage> usages = Arrays.asList(new ClassNewUsage(CLASS_NEW_USAGE));

    Iterable<ElemType> annotationTargets = Arrays.asList(ElemType.TYPE_USE, ElemType.PACKAGE);

    return new JvmClass(jvmFlags, SIGNATURE, FQ_NAME, OUT_FILE_PATH,
                        SUPER_FQ_NAME,
                        OUTER_FQ_NAME,
                        Arrays.asList(INTERFACE_1, INTERFACE_2),
                        fields,
                        methods,
                        classAnnotations,
                        annotationTargets, null,
                        usages, Collections.emptyList());
  }

  public static void checkJvmClassEquals(JvmClass jvmClass1, JvmClass jvmClass2) {
    assertEquals(jvmClass1.getSuperFqName(), jvmClass2.getSuperFqName());
    assertEquals(jvmClass1.getOuterFqName(), jvmClass2.getOuterFqName());
    assertIterableEquals(jvmClass1.getInterfaces(), jvmClass2.getInterfaces());
    checkJvmFields(jvmClass1.getFields(), jvmClass2.getFields());
    checkJvmMethodsEquals(jvmClass1.getMethods(), jvmClass2.getMethods());
    assertIterableEquals(jvmClass1.getAnnotationTargets(), jvmClass2.getAnnotationTargets());
    assertEquals(jvmClass1.getRetentionPolicy(), jvmClass2.getRetentionPolicy());
  }

  static void checkJvmFields(Iterable<JvmField> jvmFieldIterable1, Iterable<JvmField> jvmFieldIterable2) {
    Iterator<JvmField> iterator = jvmFieldIterable2.iterator();
    for (JvmField jvmField : jvmFieldIterable1) {
      JvmField jvmField2 = iterator.next();
      assertEquals(jvmField.getFlags(), jvmField2.getFlags());
      assertEquals(jvmField.getSignature(), jvmField2.getSignature());
      assertEquals(jvmField.getName(), jvmField2.getName());
      assertEquals(jvmField.getType(), jvmField2.getType());
      assertIterableEquals(jvmField.getAnnotations(), jvmField2.getAnnotations());
      assertEquals(jvmField.getValue(), jvmField2.getValue());
    }
  }

  static void checkJvmMethodsEquals(Iterable<JvmMethod> jvmMethodIterable, Iterable<JvmMethod> jvmMethodIterable2) {
    Iterator<JvmMethod> iterator2 = jvmMethodIterable2.iterator();
    for (JvmMethod jvmMethod1 : jvmMethodIterable) {
      JvmMethod jvmMethod2 = iterator2.next();
      assertEquals(jvmMethod1.getFlags(), jvmMethod2.getFlags());
      assertEquals(jvmMethod1.getSignature(), jvmMethod2.getSignature());
      assertEquals(jvmMethod1.getName(), jvmMethod2.getName());
      assertEquals(jvmMethod1.getType(), jvmMethod2.getType());
      assertIterableEquals(jvmMethod1.getAnnotations(), jvmMethod2.getAnnotations());
      assertEquals(jvmMethod1.getValue(), jvmMethod2.getValue());
      assertIterableEquals(jvmMethod1.getArgTypes(), jvmMethod2.getArgTypes());
      assertIterableEquals(jvmMethod1.getParamAnnotations(), jvmMethod2.getParamAnnotations());
      assertIterableEquals(jvmMethod1.getExceptions(), jvmMethod2.getExceptions());
      assertEquals(jvmMethod1.getDescriptor(), jvmMethod2.getDescriptor());
    }
  }
}