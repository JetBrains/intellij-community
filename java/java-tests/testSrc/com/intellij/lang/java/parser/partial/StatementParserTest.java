/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.java.parser.partial;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.JavaParsingTestCase;
import com.intellij.lang.java.parser.StatementParser;


public class StatementParserTest extends JavaParsingTestCase {
  public StatementParserTest() {
    super("parser-partial/statements");
  }

  public void testAssertNormal0() { doParserTest("{ assert cond; }"); }
  public void testAssertNormal1() { doParserTest("{ assert cond : message; }"); }

  public void testAssignmentSimple() { doParserTest("{ int[] a;\n a[0] = 0; }"); }

  public void testBlockSimple() { doParserTest("{ {} }"); }
  //public void testAnonymousInSmartCompletion() { doParserTest("{ new Foo(hash\n#) {};\n new Foo(hash\n#, bar) {};\n new Foo(hash\n#x) {}; }"); }  // todo: spaces

  public void testLocalVar() { doParserTest("{ List<Integer> list; }"); }
  public void testFor() { doParserTest("{ for(Iterator<String> it = null; it.hasNext();) { String s = it.next(); } }"); }

  //DoWhileParsingTest

  public void testForNormal0() { doParserTest("{ for(int i = 0; i < 10; i++)\n ; }"); }
  public void testForNormal1() { doParserTest("{ for( ; ; ) foo(); }"); }
  public void testForEach() { doParserTest("{ for(Object o : map.entrySet()) ; }"); }
  public void testForIncomplete0() { doParserTest("{ for }"); }
  public void testForIncomplete1() { doParserTest("{ for( }"); }
  public void testForIncomplete2() { doParserTest("{ for(int i = 0; }"); }
  public void testForIncomplete3() { doParserTest("{ for(int i = 0; i < 10 }"); }
  public void testForIncomplete4() { doParserTest("{ for(int i = 0; i < 10; }"); }
  public void testForIncomplete5() { doParserTest("{ for(int i = 0; i < 10; i++ }"); }
  public void testForIncomplete6() { doParserTest("{ for(int i = 0; i < 10; i++) }"); }
  public void testForIncomplete7() { doParserTest("{ for() foo(); }"); }
  public void testForIncomplete8() { doParserTest("{ for(int i = 0;) foo(); }"); }
  public void testForIncomplete9() { doParserTest("{ for(int i = 0; i < 0) foo(); }"); }

  public void testIfNormalWithElse() { doParserTest("{ if (a){ f1(); } else{ f2(); } }"); }
  public void testIfNormalNoElse() { doParserTest("{ if (a) f1(); }"); }
  public void testIfIncomplete0() { doParserTest("{ if }"); }
  public void testIfIncomplete1() { doParserTest("{ if ( }"); }
  public void testIfIncomplete2() { doParserTest("{ if (\n foo(); }"); }
  public void testIfIncomplete3() { doParserTest("{ if (cond }"); }
  public void testIfIncomplete4() { doParserTest("{ if (cond) }"); }
  public void testIfIncomplete5() { doParserTest("{ if () foo(); }"); }
  public void testIfIncomplete6() { doParserTest("{ if (cond) foo(); else }"); }

  public void testLabelSimple() { doParserTest("{ Loop:\n while(true) ; }"); }

  //ReturnParsingTest
  //SwitchParsingTest

  public void testSyncNormal() { doParserTest("{ synchronized(o){} }"); }
  public void testSyncIncomplete0() { doParserTest("{ synchronized }"); }
  public void testSyncIncomplete1() { doParserTest("{ synchronized( }"); }
  public void testSyncIncomplete2() { doParserTest("{ synchronized(o }"); }
  public void testSyncIncomplete3() { doParserTest("{ synchronized(o) }"); }
  public void testSyncIncomplete4() { doParserTest("{ synchronized(){} }"); }
  public void testSyncIncomplete5() { doParserTest("{ synchronized(\n foo(); }"); }

  public void testThrowNormal() { doParserTest("{ throw e; }"); }
  public void testThrowIncomplete0() { doParserTest("{ throw }"); }
  public void testThrowIncomplete1() { doParserTest("{ throw e }"); }

  public void testTryNormal0() { doParserTest("{ try{}catch(E e){} }"); }
  public void testTryNormal1() { doParserTest("{ try{}catch(E e){}finally{} }"); }
  public void testTryNormal2() { doParserTest("{ try{}finally{} }"); }
  public void testTryIncomplete0() { doParserTest("{ try }"); }
  public void testTryIncomplete1() { doParserTest("{ try{} }"); }
  public void testTryIncomplete2() { doParserTest("{ try{}catch }"); }
  public void testTryIncomplete3() { doParserTest("{ try{}catch( }"); }
  public void testTryIncomplete4() { doParserTest("{ try{}catch(E }"); }
  public void testTryIncomplete5() { doParserTest("{ try{}catch(E e }"); }
  public void testTryIncomplete6() { doParserTest("{ try{}catch(E e) }"); }
  public void testTryIncomplete7() { doParserTest("{ try{}finally }"); }

  public void testWhileNormal() { doParserTest("{ while (true) foo(); }"); }
  public void testWhileIncomplete0() { doParserTest("{ while }"); }
  public void testWhileIncomplete1() { doParserTest("{ while ( }"); }
  public void testWhileIncomplete2() { doParserTest("{ while(\n foo(); }"); }
  public void testWhileIncomplete3() { doParserTest("{ while(cond }"); }
  public void testWhileIncomplete4() { doParserTest("{ while(cond) }"); }
  public void testWhileIncomplete5() { doParserTest("{ while() foo(); }"); }

  private void doParserTest(final String text) {
    doParserTest(text, new Parser() {
      public void parse(final PsiBuilder builder) {
        StatementParser.parseCodeBlockDeep(builder, true);
      }
    });
  }
}
