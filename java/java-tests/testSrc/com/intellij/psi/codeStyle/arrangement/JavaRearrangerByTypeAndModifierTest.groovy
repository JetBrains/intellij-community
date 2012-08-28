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

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage

import static com.intellij.psi.codeStyle.arrangement.ArrangementUtil.and
import static com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType.*
import static com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier.*
/**
 * @author Denis Zhdanov
 * @since 8/28/12 6:42 PM
 */
class JavaRearrangerByTypeAndModifierTest extends AbstractRearrangerTest {

  JavaRearrangerByTypeAndModifierTest() {
    fileType = JavaFileType.INSTANCE
    language = JavaLanguage.INSTANCE
  }

  void testComplex() {
    doTest(
            '''\
class Test {
   private enum PrivateEnum {}
   protected static class ProtectedStaticInner {}
   public class PublicInner {}
   private interface PrivateInterface {}
   public abstract abstractMethod();
   private void privateMethod() {}
   public void publicMethod() {}
   private int privateField;
   private volatile int privateVolatileField;
   public int publicField;
   public static int publicStaticField;
}''',
            '''\
class Test {
   public static int publicStaticField;
   public int publicField;
   private volatile int privateVolatileField;
   private int privateField;
   public abstract abstractMethod();
   public void publicMethod() {}
   private void privateMethod() {}
   private interface PrivateInterface {}
   private enum PrivateEnum {}
   public class PublicInner {}
   protected static class ProtectedStaticInner {}
}''',
            [rule(and(atom(FIELD), atom(PUBLIC), atom(STATIC))),
             rule(and(atom(FIELD), atom(PUBLIC))),
             rule(and(atom(FIELD), atom(VOLATILE))),
             rule(and(atom(FIELD), atom(PRIVATE))),
             rule(and(atom(METHOD), atom(ABSTRACT))),
             rule(and(atom(METHOD), atom(PUBLIC))),
             rule(atom(METHOD)),
             rule(atom(INTERFACE)),
             rule(atom(ENUM)),
             rule(and(atom(CLASS), atom(PUBLIC))),
             rule(atom(CLASS))]
    )
  }
}
