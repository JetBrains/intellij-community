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
  { j = 1; }
  protected int k;
  private int i;
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
  { j = 1; }
  public int j;
  protected int k;
  private int i;
}''',
      rules: [rule(FIELD, PUBLIC), rule(FIELD, PROTECTED), rule(FIELD, PRIVATE)])
  }
}
