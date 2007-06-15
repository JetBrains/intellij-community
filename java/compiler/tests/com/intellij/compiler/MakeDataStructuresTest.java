/*
 * @author: Eugene Zhuravlev
 * Date: Apr 29, 2003
 * Time: 11:51:31 AM
 */
package com.intellij.compiler;

import com.intellij.compiler.classParsing.*;
import com.intellij.compiler.impl.InternedPath;
import com.intellij.compiler.impl.SourceUrlClassNamePair;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.StringInterner;
import junit.framework.TestCase;

public class MakeDataStructuresTest extends TestCase{

  public void testInternedPath() {
    final StringInterner interner = new StringInterner();
    final String path1 = "/a/b/c/";
    final InternedPath interned1 = new InternedPath(interner, path1, '/');
    final String path2 = "/a/b/c";
    final InternedPath interned2 = new InternedPath(interner, path2, '/');
    final String path3 = "a/b/c";
    final InternedPath interned3 = new InternedPath(interner, path3, '/');

    assertEquals(path1, interned1.toString());
    assertEquals(path2, interned2.toString());
    assertEquals(path3, interned3.toString());
  }

  public void testUrlNamePair() {
    final String url = "file://C:/work/classes/com/intellij/somePackage/SomeClass.java";
    final String className = "com.intellij.somePackage.SomeClass";
    SourceUrlClassNamePair pair = new SourceUrlClassNamePair(url, className);

    assertEquals(url, pair.getSourceUrl());
    assertEquals(className, pair.getClassName());
  }

  public void testUrlNamePair2() {
    final String url = "file://C:/work/classes/com/intellij/somePackage/SomeClass/";
    final String className = "com.intellij.somePackage.SomeClass";
    SourceUrlClassNamePair pair = new SourceUrlClassNamePair(url, className);

    assertEquals(url, pair.getSourceUrl());
    assertEquals(className, pair.getClassName());
  }

  public void testEquals() throws ClsFormatException {
    // tests equals() and hashCode() implementations of objects stored in compiler maps
    HashMap map = new HashMap();

    final String value = "dummy_value";

    map.put(new DeclarationInfo(10), value);
    assertTrue(value == map.get(new DeclarationInfo(10)));

    final FieldInfo fieldInfo = new FieldInfo(10, 20, -1, 30, ConstantValue.EMPTY_CONSTANT_VALUE, null, null);
    final MethodInfo methodInfo = new MethodInfo(10, 20, -1, 30, new int[0], false, null, null, null, null, null);

    map.put(fieldInfo, value);
    assertTrue(value == map.get(new FieldInfo(10, 20, -1, 30, ConstantValue.EMPTY_CONSTANT_VALUE, null, null)));

    map.put(methodInfo, value);
    assertTrue(value == map.get(new MethodInfo(10, 20, -1, 30, new int[0], false, null, null, null, null, null)));

    map.put(new MemberDeclarationInfo(10, fieldInfo), value);
    assertTrue(value == map.get(new MemberDeclarationInfo(10, new FieldInfo(10, 20, -1, 30, ConstantValue.EMPTY_CONSTANT_VALUE, null, null))));

    map.put(new MemberDeclarationInfo(10, methodInfo), value);
    assertTrue(value == map.get(new MemberDeclarationInfo(10, new MethodInfo(10, 20, -1, 30, new int[0], false, null, null, null, null, null))));
  }

  public void testReferenceEquality() {
    final ReferenceInfo classRef = new ReferenceInfo(10);
    final ReferenceInfo classRef2 = new ReferenceInfo(10);
    final ReferenceInfo classRef3 = new ReferenceInfo(70);
    final ReferenceInfo methodRef = new MemberReferenceInfo(10, new MethodInfo(20, 30, false));
    final ReferenceInfo methodRef2 = new MemberReferenceInfo(10, new MethodInfo(20, 30, false));
    final ReferenceInfo methodRef3 = new MemberReferenceInfo(10, new MethodInfo(20, 40, false));
    final ReferenceInfo methodRef4 = new MemberReferenceInfo(10, new MethodInfo(60, 30, false));

    assertTrue(classRef.equals(classRef2));
    assertTrue(classRef2.equals(classRef));

    assertFalse(classRef.equals(classRef3));
    assertFalse(classRef3.equals(classRef));

    assertTrue(methodRef.equals(methodRef2));
    assertTrue(methodRef2.equals(methodRef));

    assertFalse(methodRef.equals(methodRef3));
    assertFalse(methodRef3.equals(methodRef));

    assertFalse(methodRef.equals(methodRef4));
    assertFalse(methodRef4.equals(methodRef));

    assertFalse(methodRef.equals(classRef));
    assertFalse(classRef.equals(methodRef));
  }

  public void testDeclarationEquality() {
    final DeclarationInfo classDecl = new DeclarationInfo(10);
    final DeclarationInfo classDecl2 = new DeclarationInfo(10);
    final DeclarationInfo classDecl3 = new DeclarationInfo(70);
    final MemberDeclarationInfo methodDecl = new MemberDeclarationInfo(10, new MethodInfo(20, 30, false));
    final MemberDeclarationInfo methodDecl2 = new MemberDeclarationInfo(10, new MethodInfo(20, 30, false));
    final MemberDeclarationInfo methodDecl3 = new MemberDeclarationInfo(10, new MethodInfo(20, 40, false));
    final MemberDeclarationInfo methodDecl4 = new MemberDeclarationInfo(90, new MethodInfo(20, 40, false));

    assertTrue(classDecl.equals(classDecl2));
    assertTrue(classDecl2.equals(classDecl));

    assertFalse(classDecl.equals(classDecl3));
    assertFalse(classDecl3.equals(classDecl));

    assertTrue(methodDecl.equals(methodDecl2));
    assertTrue(methodDecl2.equals(methodDecl));

    assertFalse(methodDecl.equals(methodDecl3));
    assertFalse(methodDecl3.equals(methodDecl));

    assertFalse(methodDecl.equals(methodDecl4));
    assertFalse(methodDecl4.equals(methodDecl));

    assertFalse(methodDecl.equals(classDecl));
    assertFalse(classDecl.equals(methodDecl));
  }
}
