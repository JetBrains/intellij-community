/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.*

class JavaRearrangerFieldReferenceTest extends AbstractJavaRearrangerTest {

  private List<StdArrangementMatchRule> defaultFieldsArrangement = [
    rule(FIELD, STATIC, FINAL),
    rule(FIELD, PUBLIC),
    rule(FIELD, PROTECTED),
    rule(FIELD, PACKAGE_PRIVATE),
    rule(FIELD, PRIVATE)
  ]

  void "test keep referenced package private field before public one which has reference through binary expression"() {
    doTest(initial: '''\
public class TestRunnable {
    int i = 1;
    public int j = i + 1;
    public int k = 3;
    public int m = 23;
}
''',
           expected: '''\
public class TestRunnable {
    public int k = 3;
    public int m = 23;
    int i = 1;
    public int j = i + 1;
}
''',
           rules: defaultFieldsArrangement
    );
  }

  void "test keep referenced fields before those who has reference through binary expression"() {
    doTest(initial: '''\
public class javaTest {
    int i1 = 1;
    protected int i2 = i1 + 4;
}
''',
           expected: '''\
public class javaTest {
    int i1 = 1;
    protected int i2 = i1 + 4;
}
''',
           rules: defaultFieldsArrangement
    );
  }

  void "test keep referenced static fields before those who has reference through binary expression"() {
    doTest(initial: '''\
public class CodeFormatTest {
        private static String PREFIX = "prefix.";
        public static String NAME = PREFIX + "name";
        private static String PRIVATE_NAME = PREFIX + "private name";
        public static String TEST = "OK!";
        public static String BOOK = "ATLAS";
}
''',
            expected: '''\
public class CodeFormatTest {
        public static String TEST = "OK!";
        public static String BOOK = "ATLAS";
        private static String PREFIX = "prefix.";
        public static String NAME = PREFIX + "name";
        private static String PRIVATE_NAME = PREFIX + "private name";
}
''',
            rules: defaultFieldsArrangement
    );
  }

  void "test keep referenced static fields before those who has direct reference"() {
    doTest(initial: '''\
public class CodeFormatTest {
        private static String PREFIX = "prefix.";
        public static String NAME = PREFIX;
}
''',
            expected: '''\
public class CodeFormatTest {
        private static String PREFIX = "prefix.";
        public static String NAME = PREFIX;
}
''',
            rules: defaultFieldsArrangement
    );
  }

  void "test keep referenced fields before those who has direct reference"() {
    doTest(initial: '''\
public class CodeFormatTest {
        private String PREFIX = "prefix.";
        public String NAME = PREFIX;
}
''',
           expected: '''\
public class CodeFormatTest {
        private String PREFIX = "prefix.";
        public String NAME = PREFIX;
}
''',
           rules: defaultFieldsArrangement
    );
  }

  void "test keep referenced fields before those who has reference through polyadic expression"() {
    doTest(initial: '''\
public class CodeFormatTest {
        private String PREFIX = "prefix.";
        public String NAME = "ololo" + "bobob" + "line" + PREFIX + "ququ";
}
''',
           expected: '''\
public class CodeFormatTest {
        private String PREFIX = "prefix.";
        public String NAME = "ololo" + "bobob" + "line" + PREFIX + "ququ";
}
''',
           rules: defaultFieldsArrangement
    );
  }

  void "test keep referenced field before who has reference through parenthesized nested binary expression"() {
    doTest(initial: '''\
public class TestRunnable {
    int i = 3;
    public int j = (1 + i);
}
''',
           expected: '''\
public class TestRunnable {
    int i = 3;
    public int j = (1 + i);
}
''',
           rules: defaultFieldsArrangement
    );
  }



  void "test keep referenced fields before those who has reference through nested binary expression"() {
    doTest(initial: '''\
public class TestRunnable {
    int i = 3;
    public int j = (1 + 2 + (5 + (5 + (5 + i))) + (1 + (i + 1)) + (3 + i) + 5) + 4;
}
''',
           expected: '''\
public class TestRunnable {
    int i = 3;
    public int j = (1 + 2 + (5 + (5 + (5 + i))) + (1 + (i + 1)) + (3 + i) + 5) + 4;
}
''',
           rules: defaultFieldsArrangement
    );
  }


  void "test multiple references on instance fields"() {
    doTest(initial: '''\
public class TestRunnable {
    int i = 3;
    int k = 12;
    public int j = (1 + 2 + (5 + (5 + (5 + i))) + (1 + (i + 1 + k)) + (3 + i) + 5) + 4;
    public int q = 64;
}
''',
           expected: '''\
public class TestRunnable {
    public int q = 64;
    int i = 3;
    int k = 12;
    public int j = (1 + 2 + (5 + (5 + (5 + i))) + (1 + (i + 1 + k)) + (3 + i) + 5) + 4;
}
''',
           rules: defaultFieldsArrangement
    );
  }


  void "test field initializer has reference to method"() {
    doTest(initial: '''\
public class TestRunnable {
    public int foo() {
        return 15;
    }

    public int q = 64 + foo();
    int i = 3;
    int k = 12;
}
''',
           expected: '''\
public class TestRunnable {
    public int q = 64 + foo();
    int i = 3;
    int k = 12;

    public int foo() {
        return 15;
    }
}
''',
           rules: [rule(CLASS),
                   rule(FIELD, PUBLIC),
                   rule(FIELD, PACKAGE_PRIVATE),
                   rule(METHOD, PUBLIC)]
    )
  }

  void "test illegal field reference arranged to legal"() {
    doTest(initial: '''\
public class Alfa {
    int i = 3;
    public int j = i + 1 + q;
    int q = 2 + 3;
    public int r = 3;
}
''',
           expected: '''\
public class Alfa {
    public int r = 3;
    int i = 3;
    int q = 2 + 3;
    public int j = i + 1 + q;
}
''',
           rules: defaultFieldsArrangement
    );
  }

  void "test field references work ok with enums"() {
    doTest(
      initial: '''\
public class Q {
    private static final Q A = new Q(Q.E.EC);
    private static final Q B = new Q(Q.E.EB);
    private static final Q C = new Q(Q.E.EA);
    private static final Q D = new Q(Q.E.EA);
    private final E e;
    private static final int seven = 7;

    private Q(final Q.E e) {
        this.e = e;
    }

    public static enum E {
        EA,
        EB,
        EC,
    }
}
''',
      expected: '''\
public class Q {
    private static final Q A = new Q(Q.E.EC);
    private static final Q B = new Q(Q.E.EB);
    private static final Q C = new Q(Q.E.EA);
    private static final Q D = new Q(Q.E.EA);
    private static final int seven = 7;
    private final E e;

    private Q(final Q.E e) {
        this.e = e;
    }

    public static enum E {
        EA,
        EB,
        EC,
    }
}
''',
      rules: defaultFieldsArrangement
    )
  }

  void "test IDEA-123733"() {
    doTest(
      initial: '''\
class First {
    protected int test = 12;
}

class Second extends First {
    void test() {}

    private int q = test;
    public int t = q;
}
''',
      expected: '''\
class First {
    protected int test = 12;
}

class Second extends First {
    private int q = test;
    public int t = q;

    void test() {}
}
''',
      rules: defaultFieldsArrangement
    )
  }

  void "test IDEA-123875"() {
    doTest(
      initial: '''\
public class RearrangeFail {

    public static final byte[] ENTITIES_END = "</entities>".getBytes();
    private final Element entitiesEndElement = new Element(ENTITIES_END);

    public static final byte[] ENTITIES_START = "<entities>".getBytes();
    private final Element entitiesStartElement = new Element(ENTITIES_START);

}
''',
      expected: '''\
public class RearrangeFail {

    public static final byte[] ENTITIES_END = "</entities>".getBytes();
    public static final byte[] ENTITIES_START = "<entities>".getBytes();
    private final Element entitiesEndElement = new Element(ENTITIES_END);
    private final Element entitiesStartElement = new Element(ENTITIES_START);

}
''',
      rules: [
        rule(PUBLIC, STATIC, FINAL),
        rule(PRIVATE),
      ]
    )
  }

  void "test IDEA-125099"() {
    doTest(
      initial: '''\
public class test {

    private int a = 2;

    public static final String TEST = "1";
    public static final String SHOULD_BE_IN_BETWEEN = "2";
    public static final String USERS_ROLE_ID_COLUMN = TEST;
}
''',
      expected: '''\
public class test {

    public static final String TEST = "1";
    public static final String SHOULD_BE_IN_BETWEEN = "2";
    public static final String USERS_ROLE_ID_COLUMN = TEST;
    private int a = 2;
}
''',
      rules: [
        rule(PUBLIC, STATIC, FINAL),
        rule(PRIVATE)
      ]
    )
  }
  
  void "test IDEA-128071"() {
    doTest(
      initial: '''
public class FormatTest {
    public int a = 3;
    private static final String FACEBOOK_CLIENT_ID = "";
    public static final String FACEBOOK_OAUTH_URL = "".concat(FACEBOOK_CLIENT_ID).concat("");
}
''',
      expected: '''
public class FormatTest {
    private static final String FACEBOOK_CLIENT_ID = "";
    public static final String FACEBOOK_OAUTH_URL = "".concat(FACEBOOK_CLIENT_ID).concat("");
    public int a = 3;
}
''',
      rules: [
        rule(PUBLIC, STATIC, FINAL),
        rule(PRIVATE, STATIC, FINAL),
        rule(PUBLIC)
      ]
    )
  }

  void "test field dependency through method call"() {
    doTest(
      initial: '''
public class TmpTest {
    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
    static final String SUB_MESSAGE_REQUEST_SNAPSHOT = create(1);

    private static String create(int i) {
        return Integer.toString(i + EMPTY_OBJECT_ARRAY.length);
    }

    public static void main(String[] args) {
        System.out.println(SUB_MESSAGE_REQUEST_SNAPSHOT);
    }
}
''',
      expected: '''
public class TmpTest {
    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
    static final String SUB_MESSAGE_REQUEST_SNAPSHOT = create(1);

    public static void main(String[] args) {
        System.out.println(SUB_MESSAGE_REQUEST_SNAPSHOT);
    }

    private static String create(int i) {
        return Integer.toString(i + EMPTY_OBJECT_ARRAY.length);
    }
}
''',
      rules: [
        rule(FIELD),
        rule(PRIVATE, FIELD),
        rule(PUBLIC, METHOD),
        rule(PRIVATE, METHOD)
      ]
    );
  }

  void "test only dependencies withing same initialization scope"() {
    doTest(
      initial: '''
public class TestArrangementBuilder {
    private String theString = "";
    private static final TestArrangement DEFAULT = new TestArrangementBuilder().build();

    public TestArrangement build() {
        return new TestArrangement(theString);
    }

    public class TestArrangement {
        private final String theString;

        public TestArrangement() {
            this("");
        }

        public TestArrangement(@NotNull String aString) {
            theString = aString;
        }
    }
}
''',
      expected: '''
public class TestArrangementBuilder {
    private static final TestArrangement DEFAULT = new TestArrangementBuilder().build();
    private String theString = "";

    public TestArrangement build() {
        return new TestArrangement(theString);
    }

    public class TestArrangement {
        private final String theString;

        public TestArrangement() {
            this("");
        }

        public TestArrangement(@NotNull String aString) {
            theString = aString;
        }
    }
}
''',
      rules: [
        rule(PUBLIC, STATIC, FINAL),
        rule(PRIVATE, STATIC, FINAL),
        rule(PRIVATE, FINAL),
        rule(PRIVATE)
      ]
    )
  }


}
