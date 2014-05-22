/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PRIVATE
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PUBLIC
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.FIELD
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.METHOD
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.CLASS
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.INTERFACE
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.ENUM

class JavaRearrangerSectionsTest extends AbstractJavaRearrangerTest {

  void "test single section"() {
    doTest(
      initial: '''
class MyClass
{
  // --- Public Methods ---

  public void test() {}

  // --- End Public Methods ---

  private int number;
}''',
      expected: '''
class MyClass
{
  private int number;

  // --- Public Methods ---
  public void test() {}
  // --- End Public Methods ---
}''',
      rules: [
        rule(FIELD),
        section("// --- Public Methods ---", "// --- End Public Methods ---", rule(METHOD))]
    )
  }

  void "test multi section"() {
    doTest(
      initial: '''
class MyClass
{
  // --- Private Methods ---

  private void foo() {}

  // --- End Private Methods ---

  // --- Public Methods ---

  public void test() {}

  // --- End Public Methods ---

  public int p;
}''',
      expected: '''
class MyClass
{
  public int p;

  // --- Public Methods ---
  public void test() {}
  // --- End Public Methods ---

  // --- Private Methods ---
  private void foo() {}
  // --- End Private Methods ---
}''',
      rules: [
        section(rule(FIELD)),
        section("// --- Public Methods ---", "// --- End Public Methods ---", rule(PUBLIC, METHOD)),
        section("// --- Private Methods ---", "// --- End Private Methods ---", rule(PRIVATE, METHOD))
      ]
    )
  }


  void "test multi region"() {
    doTest(
      initial: '''
class MyClass
{
  //region Private Methods

  private void foo() {}

  //endregion private

  //region Public Methods

  public void test() {}

  //endregion public

  public int p;
}''',
      expected: '''
class MyClass
{
  public int p;

  //region Public Methods
  public void test() {}
  //endregion public

  //region Private Methods
  private void foo() {}
  //endregion private
}''',
      rules: [
        section(rule(FIELD)),
        section("//region Public Methods", "//endregion public", rule(PUBLIC, METHOD)),
        section("//region Private Methods", "//endregion private", rule(PRIVATE, METHOD))]
    )
  }


  void "test new single section"() {
    doTest(
      initial: '''
class MyClass
{
  public void test() {}

  public int p;
}''',
      expected: '''
class MyClass
{
  public int p;

// --- Public Methods ---
  public void test() {}
// --- End Public Methods ---
}''',
      rules: [
        section(rule(FIELD)),
        section("// --- Public Methods ---", "// --- End Public Methods ---", rule(METHOD))]
    )
  }


  void "test all new multi section without comments"() {
    doTest(
      initial: '''
class MyClass
{
  public void test() {}

  public int p;
}''',
      expected: '''
class MyClass
{
  public int p;

// --- Public Methods ---
  public void test() {}
// --- End Public Methods ---
}''',
      rules: [
        section(rule(FIELD)),
        section("// --- Public Methods ---", "// --- End Public Methods ---", rule(METHOD))
      ]
    )
  }


  void "test new multi section"() {
    doTest(
      initial: '''
class MyClass
{
  public int p;

  public void test() {}
}''',
      expected: '''
class MyClass
{
// --- Fields ---
  public int p;
// --- Fields ---

// --- Public Methods ---
  public void test() {}
// --- End Public Methods ---
}''',
      rules: [
        section("// --- Fields ---", "// --- Fields ---", rule(FIELD)),
        section("// --- Public Methods ---", "// --- End Public Methods ---", rule(METHOD))]
    )
  }


  void "test new not arranged multi section"() {
    doTest(
      initial: '''
class MyClass
{
  public void test() {}

  public int p;
}''',
      expected: '''
class MyClass
{
// --- Fields ---
  public int p;
// --- Fields ---

// --- Public Methods ---
  public void test() {}
// --- End Public Methods ---
}''',
      rules: [
        section("// --- Fields ---", "// --- Fields ---", rule(FIELD)),
        section("// --- Public Methods ---", "// --- End Public Methods ---", rule(METHOD))
      ]
    )
  }

  void "test blank lines"() {
    doTest(
      initial: '''
class MyClass
{
  // --- Public Methods ---
  public void test() {}
  // --- End Public Methods ---

  public int p;
}''',
      expected: '''
class MyClass
{
  public int p;

  // --- Public Methods ---
  public void test() {}
  // --- End Public Methods ---
}''',
      rules: [
        rule(FIELD),
        section("// --- Public Methods ---", "// --- End Public Methods ---", rule(METHOD))]
    )
  }

  void "test multi rules in section"() {
    doTest(
      initial: '''
class MyClass
{
  private void foo() {
    System.out.println("Hellow!");
  }

  // --- Methods ---
  public void test() {}
  // --- End Methods ---

  public int p;
}''',
      expected: '''
class MyClass
{
  public int p;

  // --- Methods ---
  public void test() {}

  private void foo() {
    System.out.println("Hellow!");
  }
  // --- End Methods ---
}''',
      rules: [
        rule(FIELD),
        section("// --- Methods ---", "// --- End Methods ---", rule(PUBLIC, METHOD), rule(PRIVATE, METHOD))]
    )
  }

  void "test multi rules in new section"() {
    doTest(
      initial: '''
class MyClass
{
  private int i;

  private void foo() {
    System.out.println("Hellow!");
  }

  // --- Methods ---
  public void test() {}
  // --- End Methods ---

  public int p;
}''',
      expected: '''
class MyClass
{
// --- Properties ---
  public int p;
  private int i;
// --- End Properties ---

  // --- Methods ---
  public void test() {}

  private void foo() {
    System.out.println("Hellow!");
  }
  // --- End Methods ---
}''',
      rules: [
        section("// --- Properties ---", "// --- End Properties ---", rule(PUBLIC, FIELD), rule(PRIVATE, FIELD)),
        section("// --- Methods ---", "// --- End Methods ---", rule(PUBLIC, METHOD), rule(PRIVATE, METHOD))]
    )
  }


  void "test section without close comment"() {
    doTest(
      initial: '''
class MyClass
{
  private void foo() {
    System.out.println("Hellow!");
  }

  // --- Methods ---
  public void test() {}

  public int p;
}''',
      expected: '''
class MyClass
{
  public int p;

  // --- Methods ---
  public void test() {}

  private void foo() {
    System.out.println("Hellow!");
  }
}''',
      rules: [
        section(rule(FIELD)),
        section("// --- Methods ---", null, rule(PUBLIC, METHOD), rule(PRIVATE, METHOD))
      ]
    )
  }

  void "test new section without close comment"() {
    doTest(
      initial: '''
class MyClass
{
  private void foo() {
    System.out.println("Hellow!");
  }

  // --- Methods ---
  public void test() {}

  public int p;
}''',
      expected: '''
class MyClass
{
// --- Properties ---
  public int p;

  // --- Methods ---
  public void test() {}

  private void foo() {
    System.out.println("Hellow!");
  }
}''',
      rules: [
        section("// --- Properties ---", null, rule(FIELD)),
        section("// --- Methods ---", null, rule(PUBLIC, METHOD), rule(PRIVATE, METHOD))
      ]
    )
  }

  void "test new section in hierarchy"() {
    doTest(
      initial: '''
class MyClass
{
  private void foo() {
    System.out.println("Hellow!");
  }
  public void test() {}
}''',
      expected: '''
//region --- Class ---
class MyClass
{
//region --- Methods ---
  public void test() {}

  private void foo() {
    System.out.println("Hellow!");
  }
//endregion methods
}
//endregion class''',
      rules: [
        section("//region --- Class ---", "//endregion class", rule(CLASS)),
        section("//region --- Methods ---", "//endregion methods", rule(PUBLIC, METHOD), rule(PRIVATE, METHOD))
      ]
    )
  }

  void "test new sections in hierarchy"() {
    doTest(
      initial: '''
interface MyI {
}

class MyClass
{
  private void foo() {
    System.out.println("Hellow!");
  }
  public void test() {}
}''',
      expected: '''
//region --- Class ---
class MyClass
{
//region --- Methods ---
  public void test() {}

  private void foo() {
    System.out.println("Hellow!");
  }
//endregion methods
}

interface MyI {
}
//endregion class''',
      rules: [
        section("//region --- Class ---", "//endregion class", rule(CLASS), rule(INTERFACE)),
        section("//region --- Methods ---", "//endregion methods", rule(PUBLIC, METHOD), rule(PRIVATE, METHOD))]
    )
  }

  void "test two methods section comments in a row"() {
    doTest(
      initial:'''
public class SuperTest {
    //publicS
    public void test() {
    }
//publicE
//privateS
    private void testPrivate() {
    }
//privateE

    public int t;
}
''',
      expected:'''
public class SuperTest {
    public int t;

    //publicS
    public void test() {
    }
//publicE

//privateS
    private void testPrivate() {
    }
//privateE
}
''',
      rules: [
        rule(FIELD),
        section("//publicS", "//publicE", rule(PUBLIC, METHOD)),
        section("//privateS", "//privateE", rule(PRIVATE, METHOD))
      ]
    );
  }

  void "test two fields section comments in a row"() {
    doTest(
      initial:'''
public class SuperTest {
    public void test() {}

    //publicFieldStart
    public int test = 2;
//publicFieldEnd
//privateFieldStart
    private int pr = 1;
//privateFieldEnd
}
''',
      expected:'''
public class SuperTest {
    //publicFieldStart
    public int test = 2;
//publicFieldEnd
//privateFieldStart
    private int pr = 1;
//privateFieldEnd

    public void test() {}
}
''',
      rules: [
        section("//publicFieldStart", "//publicFieldEnd", rule(PUBLIC, FIELD)),
        section("//privateFieldStart", "//privateFieldEnd", rule(PRIVATE, FIELD)),
        rule(METHOD)
      ]
    );
  }

  void "test no additional sections added"() {
    doTest(
      initial: '''
//sectionClassStart
public class Test {
}
//sectionClassEnd
''',
      expected: '''
//sectionClassStart
public class Test {
}
//sectionClassEnd
''',
      rules: [
        section("//sectionClassStart", "//sectionClassEnd", rule(CLASS))
      ]
    )
  }

  void "test inner classes sections"() {
    doTest(
      initial: '''
//sectionClassStart
public class Test {

    interface Testable {
    }

}
//sectionClassEnd
class Double {
}
''',
      expected: '''
//sectionClassStart
public class Test {

//sectionInterfaceStart
    interface Testable {
    }
//sectionInterfaceEnd

}

class Double {
}
//sectionClassEnd
''',
      rules: [
        section("//sectionClassStart", "//sectionClassEnd", rule(CLASS)),
        section("//sectionInterfaceStart", "//sectionInterfaceEnd", rule(INTERFACE))
      ]
    )
  }

  void "test a lot of sections"() {
    def rules = [
      section("//sectionInterfaceStart", "//sectionInterfaceEnd", rule(INTERFACE)),
      section("//sectionEnumStart", "//sectionEnumEnd", rule(ENUM)),
      section("//sectionClassStart", "//sectionClassEnd", rule(CLASS)),
      section("//sectionPublicFieldStart", "//sectionPublicFieldEnd", rule(FIELD, PUBLIC)),
      section("//sectionPrivateFieldStart", "//sectionPrivateFieldEnd", rule(FIELD, PRIVATE)),
      section("//sectionPubicMethodStart", "//sectionPublicMethodEnd", rule(METHOD, PUBLIC)),
      section("//sectionPrivateMethodStart", "//sectionPrivateMethodEnd", rule(METHOD, PRIVATE)),
    ]

    doTest(
      initial: '''
public class SuperTest {

    class T {}

    class R {}

    interface I {}

    enum Q {}

    public void test() {}

    private void teste2() {}

    public void testtt() {}

    public int a;

    private int b;
}

class Test {}
''',
      expected: '''
//sectionClassStart
public class SuperTest {

//sectionInterfaceStart
    interface I {}
//sectionInterfaceEnd

//sectionEnumStart
    enum Q {}
//sectionEnumEnd

//sectionClassStart
    class T {}

    class R {}
//sectionClassEnd
//sectionPublicFieldStart
    public int a;
//sectionPublicFieldEnd
//sectionPrivateFieldStart
    private int b;
//sectionPrivateFieldEnd

//sectionPubicMethodStart
    public void test() {}

    public void testtt() {}
//sectionPublicMethodEnd

//sectionPrivateMethodStart
    private void teste2() {}
//sectionPrivateMethodEnd
}

class Test {}
//sectionClassEnd
''',
      rules: rules
    )

    doTest(
      initial: '''
//sectionClassStart
public class SuperTest {

//sectionInterfaceStart
    interface I {}
//sectionInterfaceEnd

//sectionEnumStart
    enum Q {}
//sectionEnumEnd

//sectionClassStart
    class T {}

    class R {}
//sectionClassEnd

//sectionPublicFieldStart
    public int a;
//sectionPublicFieldEnd

//sectionPrivateFieldStart
    private int b;
//sectionPrivateFieldEnd

//sectionPubicMethodStart
    public void test() {}

    public void testtt() {}
//sectionPublicMethodEnd

//sectionPrivateMethodStart
    private void teste2() {}
//sectionPrivateMethodEnd

    private void newPrivateTest() {}

    class NewClass {}

    interface NewInterface {}
}

class Test {}
//sectionClassEnd

class NewOuterClass {}
''',
      expected: '''
//sectionClassStart
public class SuperTest {

//sectionInterfaceStart
    interface I {}
    interface NewInterface {}
//sectionInterfaceEnd
//sectionEnumStart
    enum Q {}
//sectionEnumEnd

//sectionClassStart
    class T {}

    class R {}

    class NewClass {}
//sectionClassEnd
//sectionPublicFieldStart
    public int a;
//sectionPublicFieldEnd
//sectionPrivateFieldStart
    private int b;
//sectionPrivateFieldEnd

//sectionPubicMethodStart
    public void test() {}

    public void testtt() {}
//sectionPublicMethodEnd

//sectionPrivateMethodStart
    private void teste2() {}

    private void newPrivateTest() {}
//sectionPrivateMethodEnd
}

class Test {}

class NewOuterClass {}
//sectionClassEnd
''',
      rules: rules
    )
  }

  void "test range rearrangement"() {
    doTest(
      initial: '''
public class Test {
    //pubMS
<range>    public void test1() {}
    private void test2() {}
    public void test3() {}</range>
    //pubME

    //privMS
    private void test4() {}
    //privME
}
''',
      expected: '''
public class Test {
    //pubMS
    public void test1() {}

    public void test3() {}
    //pubME

    //privMS
    private void test2() {}
    private void test4() {}
    //privME
}
''',
      rules: [
        section("//pubMS", "//pubME", rule(PUBLIC, METHOD)),
        section("//privMS", "//privME", rule(PRIVATE, METHOD))
      ]
    )
  }

  void "test on range with nested sections"() {
    doTest(
      initial: '''
//classStart
public class Test {

    //classStart
<range>    class R {}
    class T {}
    public void test() {}</range>
    //classEnd

    //publicMethodStart
    public void tester() {}
    //publicMethodEnd
}

class NewOne {
}
//classEnd
''',
      expected: '''
//classStart
public class Test {

    //classStart
    class R {}
    class T {}
    //classEnd

    //publicMethodStart
    public void test() {}
    public void tester() {}
    //publicMethodEnd
}

class NewOne {
}
//classEnd
''',
      rules: [
        section("//classStart", "//classEnd", rule(CLASS)),
        section("//publicMethodStart", "//publicMethodEnd", rule(PUBLIC, METHOD))
      ]
    )
  }

  //Now comes failing tests - to fix in future

  //TODO look at upper one - it succeeds this is not!!!
  void "do not test on range with three inner sections"() {
    doTest(
      initial: '''
//classStart
public class Test {

    //classStart
<range>    class R {}
    class T {}
    public void test() {}</range>
    //classEnd

    //fieldStart
    public int i = 1;
    //fieldEnd

    //publicMethodStart
    public void tester() {}
    //publicMethodEnd
}

class NewOne {
}
//classEnd
''',
      expected: '''
//classStart
public class Test {

    //classStart
    class R {}
    class T {}
    //classEnd

    //fieldStart
    public int i = 1;
    //fieldEnd

    //publicMethodStart
    public void test() {}
    public void tester() {}
    //publicMethodEnd
}

class NewOne {
}
//classEnd
''',
      rules: [
        section("//classStart", "//classEnd", rule(CLASS)),
        section("//fieldStart", "//fieldEnd", rule(FIELD)),
        section("//publicMethodStart", "//publicMethodEnd", rule(PUBLIC, METHOD))
      ]
    )
  }

  void "do not test field has not only section comments"() {
    doTest(
      initial: '''\
class Test {

  //method start
  //field1
  public int field1 = 1;

  //method end

  public void method test() {}

}
''',
      expected: '''\
class Test {

  //field1
  public int field1 = 1;

  //method start
  public void method test() {}
  //method end

}
''',
      rules: [
        rule(FIELD),
        section("//method start", "//method end", rule(METHOD))
      ]
    )
  }


  void "do not test class has not only section comments"() {
    doTest(
      initial: '''\
//class start
//main class
public class Test {
}
//class end

interface I {
}

class A {
}

class B {
}
''',
      expected: '''\
interface I {
}

//class start
//main class
public class Test {
}

class A {
}

class B {
}
//class end
''',
      rules: [
        rule(INTERFACE),
        section("//class start", "//class end", rule(CLASS))
      ]
    )
  }

  void "do not test method has not only section comments"() {
    doTest(
      initial: '''\
class Test {

  //methods start
  //first
  public void test() {}

  private void t() {}
  //method end
}
''',
      expected: '''\
class Test {

  //methods start
  private void t() {}

  //first
  public void test() {}
  //method end

}
''',
      rules: [
        section("//methods start", "//method end", rule(PRIVATE, METHOD), rule(PUBLIC, METHOD))
      ]
    )
  }

}
