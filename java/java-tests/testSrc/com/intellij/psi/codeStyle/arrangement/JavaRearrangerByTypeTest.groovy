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
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType
import com.intellij.psi.codeStyle.arrangement.match.ByTypeArrangementEntryMatcher
/**
 * @author Denis Zhdanov
 * @since 7/20/12 2:45 PM
 */
class JavaRearrangerByTypeTest extends AbstractRearrangerTest {

  JavaRearrangerByTypeTest() {
    fileType = JavaFileType.INSTANCE
  }

  void testFieldsBeforeMethods() {
    doTest(
            '''\
class Test {
  public void test() {}
   private int i;
}
class Test2 {
public void test() {
}
    private int i;
  private int j;
}''',
            '''\
class Test {
   private int i;
  public void test() {}
}
class Test2 {
    private int i;
  private int j;
public void test() {
}
}''',
            [new ArrangementRule(new ByTypeArrangementEntryMatcher(ArrangementEntryType.FIELD))]
    )
  }

  void testAnonymousClassAtFieldInitializer() {
    doTest(
            '''\
class Test {
  private Object first = new Object() {
    int inner1;
    public String toString() { return "test"; }
    int inner2;
  };
  public Object test(Object ... args) {
    return null;
  }
  private Object second = test(test(new Object() {
    public String toString() {
      return "test";
    }
    private Object inner = new Object() {
      public String toString() { return "innerTest"; }
    };
  }));
}''',
            '''\
class Test {
  private Object first = new Object() {
    int inner1;
    int inner2;
    public String toString() { return "test"; }
  };
  private Object second = test(test(new Object() {
    private Object inner = new Object() {
      public String toString() { return "innerTest"; }
    };
    public String toString() {
      return "test";
    }
  }));
  public Object test(Object ... args) {
    return null;
  }
}''',
            [new ArrangementRule(new ByTypeArrangementEntryMatcher(ArrangementEntryType.FIELD))]
    )
  }
  
  void testAnonymousClassAtMethod() {
    doTest(
            '''\
class Test {
   void declaration() {
     Object o = new Object() {
       private int test() { return 1; }
       String s;
     }
   }
   double d;
   void call() {
     test(test(1, new Object() {
       public void test() {}
       int i;
     });
   }
}''',
            '''\
class Test {
   double d;
   void declaration() {
     Object o = new Object() {
       String s;
       private int test() { return 1; }
     }
   }
   void call() {
     test(test(1, new Object() {
       int i;
       public void test() {}
     });
   }
}''',
            [new ArrangementRule(new ByTypeArrangementEntryMatcher(ArrangementEntryType.FIELD))]
    )
  }
  
  void testRanges() {
    doTest(
            '''\
class Test {
  void outer1() {}
<range>  String outer2() {}
  int i;</range>
  void test() {
    method(new Object() {
      void inner1() {}
      Object field = new Object() {
<range>        void inner2() {}
        String s;</range>
        Integer i;
      }
    });
  }
}''',
            '''\
class Test {
  void outer1() {}
  int i;
  String outer2() {}
  void test() {
    method(new Object() {
      void inner1() {}
      Object field = new Object() {
        String s;
        void inner2() {}
        Integer i;
      }
    });
  }
}''',
            [new ArrangementRule(new ByTypeArrangementEntryMatcher(ArrangementEntryType.FIELD))]
    )
  }
}
