/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.*

/**
 * @author Denis Zhdanov
 * @since 8/28/12 6:42 PM
 */
class JavaRearrangerByTypeAndModifierTest extends AbstractJavaRearrangerTest {

  void "test complex sample"() {
    commonSettings.BLANK_LINES_AROUND_METHOD = 0
    commonSettings.BLANK_LINES_AROUND_CLASS = 0

    doTest(
      initial: '''\
class Test {
   private enum PrivateEnum {}
   protected static class ProtectedStaticInner {}
   public class PublicInner {}
   private interface PrivateInterface {}
   public abstract void abstractMethod();
   private void privateMethod() {}
   public void publicMethod() {}
   private int privateField;
   private volatile int privateVolatileField;
   public int publicField;
   public static int publicStaticField;
}''',
      expected: '''\
class Test {
   public static int publicStaticField;
   public int publicField;
   private volatile int privateVolatileField;
   private int privateField;
   public abstract void abstractMethod();
   public void publicMethod() {}
   private void privateMethod() {}
   private interface PrivateInterface {}
   private enum PrivateEnum {}
   public class PublicInner {}
   protected static class ProtectedStaticInner {}
}''',
      rules: [rule(FIELD, PUBLIC, STATIC),
              rule(FIELD, PUBLIC),
              rule(FIELD, VOLATILE),
              rule(FIELD, PRIVATE),
              rule(METHOD, ABSTRACT),
              rule(METHOD, PUBLIC),
              rule(METHOD),
              rule(INTERFACE),
              rule(ENUM),
              rule(CLASS, PUBLIC),
              rule(CLASS)]
    )
  }

  void "test instance initialization block bound to a field"() {
    doTest(
      initial: '''\
class Test {
  private int i;
  public int j;
  { j = 1; }
  protected int k;
}''',
      expected: '''\
class Test {
  public int j;
  protected int k;
  private int i;

  { j = 1; }
}''',
      rules: [rule(FIELD, PUBLIC), rule(FIELD, PROTECTED), rule(FIELD, PRIVATE)])
  }

  void "test instance initialization block as the first child"() {
    doTest(
      initial: '''\
class Test {
  { j = 1; }
  private int i;
  public int j;
  protected int k;
}''',
      expected: '''\
class Test {
  public int j;
  protected int k;
  private int i;

  { j = 1; }
}''',
      rules: [rule(FIELD, PUBLIC), rule(FIELD, PROTECTED), rule(FIELD, PRIVATE)])
  }

  void "test getter is matched by public method rule"() {
    doTest(
      initial: '''\
class Test {
  private void test() {}

  private int count;

  private void run() {}

  public int getCount() {
    return count;
  }

  private void compose() {}
}
''',
      expected: '''\
class Test {
  private int count;

  private void test() {}

  private void run() {}

  private void compose() {}

  public int getCount() {
    return count;
  }
}
''',
      rules: [rule(FIELD), rule(PRIVATE, METHOD), rule(PUBLIC, METHOD)]
    );
  }

  void "test getter is matched by method rule"() {
    doTest(
      initial: '''\
class Test {
  public int getCount() {
    return count;
  }

  class T {}

  private int count;

  private void compose() {}
}
''',
      expected: '''\
class Test {
  private int count;

  public int getCount() {
    return count;
  }

  private void compose() {}

  class T {}
}
''',
      rules: [rule(FIELD), rule(METHOD)]
    );
  }

  void "test getter is not matched by private method"() {
    doTest(
      initial: '''\
class Test {
  private int count;

  public int getCount() {
    return count;
  }

  private void compose() {}
}
''',
      expected: '''\
class Test {
  private void compose() {}
  private int count;

  public int getCount() {
    return count;
  }
}
''',
      rules: [rule(PRIVATE, METHOD), rule(FIELD)]
    )
  }

  void "test getter is matched by getter rule"() {
    doTest(
      initial: '''\
class Test {
  private void test() {}

  private int count;

  private void run() {}

  public int getCount() {
    return count;
  }

  public int superRun() {}

  private void compose() {}
}
''',
      expected: '''\
class Test {
  private int count;

  public int getCount() {
    return count;
  }

  private void test() {}

  private void run() {}

  private void compose() {}

  public int superRun() {}
}
''',
      rules: [rule(FIELD), rule(GETTER), rule(PRIVATE, METHOD), rule(PUBLIC, METHOD)]
    );
  }

  void "test setter is matched by public method rule"() {
    doTest(
      initial: '''\
class Test {
  private void test() {}

  private int count;

  private void run() {}

  public void setCount(int value) {
    count = value;
  }

  private void compose() {}
}
''',
      expected: '''\
class Test {
  private int count;

  private void test() {}

  private void run() {}

  private void compose() {}

  public void setCount(int value) {
    count = value;
  }
}
''',
      rules: [rule(FIELD), rule(PRIVATE, METHOD), rule(PUBLIC, METHOD)]
    )
  }

  void "test setter is matched by method rule"() {
    doTest(
      initial: '''\
class Test {
  public void setCount(int value) {
    count = value;
  }

  class T {}

  private int count;

  private void compose() {}
}
''',
      expected: '''\
class Test {
  private int count;

  public void setCount(int value) {
    count = value;
  }

  private void compose() {}

  class T {}
}
''',
      rules: [rule(FIELD), rule(METHOD)]
    );
  }

  void "test setter is not matched by private method"() {
    doTest(
      initial: '''\
class Test {
  private int count;

  public void setCount(int value) {
    count = value;
  }

  private void compose() {}
}
''',
      expected: '''\
class Test {
  private void compose() {}
  private int count;

  public void setCount(int value) {
    count = value;
  }
}
''',
      rules: [rule(PRIVATE, METHOD), rule(FIELD)]
    )
  }

  void "test setter is matched by setter rule"() {
    doTest(
      initial: '''\
class Test {
  private void test() {}

  private int count;

  private void run() {}

  public void setCount(int value) {
    count = value;
  }

  public int superRun() {}

  private void compose() {}
}
''',
      expected: '''\
class Test {
  private int count;

  public void setCount(int value) {
    count = value;
  }

  private void test() {}

  private void run() {}

  private void compose() {}

  public int superRun() {}
}
''',
      rules: [rule(FIELD), rule(SETTER), rule(PRIVATE, METHOD), rule(PUBLIC, METHOD)]
    )
  }

  void "test setters and getters"() {
    doTest(
      initial: '''\
class Test {
  private void test() {}

  private void run() {}

  public void setCount(int value) {
    count = value;
  }

  private int count;

  public int getCount() {
    return count;
  }

  private void compose() {}

  public int superRun() {}
}
''',
      expected: '''\
class Test {
  private int count;

  public int getCount() {
    return count;
  }

  public void setCount(int value) {
    count = value;
  }

  private void test() {}

  private void run() {}

  private void compose() {}

  public int superRun() {}
}
''',
      rules: [rule(FIELD), rule(GETTER), rule(SETTER), rule(METHOD, PRIVATE), rule(METHOD, PUBLIC)]
    )
  }
}
