/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.igtest.performance.string_concatenation_in_loops;

import java.util.*;

public class StringConcatenationInLoop
{
    public StringConcatenationInLoop()
    {
    }

    public String foo()
    {
        String foo = "";
        for(int i = 0; i < 5; i++)
        {
            (foo) = ((foo) <warning descr="String concatenation '+' in loop">+</warning> ("  ") + (i));
            foo += "abc"; // only first concatenation in the loop is reported for given variable
        }
        for(int i = 0; i < 5; i++)
        {
            foo <warning descr="String concatenation '+=' in loop">+=</warning> foo + "  " + i;
        }
        for(int i = 0; i < 5; i++)
        {
            baz( foo + "  " + i);
        }
        for(int i = 0; i < 5; i++)
        {
            if(bar())
            {
                return baz(("foo" + "bar"));
            }
        }
        for(int i = 0; i < 5; i++)
        {
            if(bar())
            {
                throw new Error("foo" + i);
            }
        }
        String s = "";
        for(int i = 0; i < 5; i++) {
            if(i > 2) {
                s += i;
                {
                    System.out.println(s);
                    break;
                }
            }
        }
        for(int i = 0; i < 5; i++) {
            if(i > 2) {
                s += i;
                {
                    System.out.println(s);
                    {
                        break;
                    }
                }
            }
        }
        for(int i = 0; i < 5; i++) {
            if(i > 2) {
                s += i;
                {
                    {
                        System.out.println(i);
                    }
                    break;
                }
            }
        }
        for(int i = 0; i < 5; i++) {
            if(i > 2) {
                s <warning descr="String concatenation '+=' in loop">+=</warning> i;
                {
                    System.out.println(i);
                }
                System.out.println(i);
            }
        }
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                // concatenated only on single iteration
                s += i;
            }
            System.out.println(i);
        }
        List<String> strings = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            s += i;
            if (s.length() > 0) {
                strings.add(s); // the whole string is used on every iteration anyways: it's useless to create a StringBuilder here
            }
        }
        for (int i = 0; i < 10; i++) {
            s <warning descr="String concatenation '+=' in loop">+=</warning> i;
            if (i == 5 && s.length() > 0) {
                strings.add(s); // the whole string is used, but only once: it's still reasonable to migrate to StringBuilder
            }
        }
        for (int i = 0; i < 10; i++) {
            s <warning descr="String concatenation '+=' in loop">+=</warning> i;
            if (s.length() > 50) {
                strings.add(s); // the whole string is used, but it's recreated after the usage: it's still reasonable to migrate to StringBuilder
                s = "";
            }
        }
        for (int i = 0; i < 10; i++) {
            s = (s == "") + "...";
        }
        for (int i = 0; i < 10; i++) {
            s = ("xyz" <warning descr="String concatenation '+' in loop">+</warning> (i <warning descr="String concatenation '+' in loop">+</warning> s)) <warning descr="String concatenation '+' in loop">+</warning> "...";
        }
        System.out.println(foo);
        return foo;
    }

    void test(String s) {
        for(int i=0; i<10; i++) {
            if(s != null) {
                if(s.equals("xyz")) {
                    s += "xyz";
                }
                break;
            }
        }
    }

    void test2(String s, int flag) {
        for(int i=0; i<10; i++) {
            if(flag == 0) {
                s<warning descr="String concatenation '+=' in loop">+=</warning>"xyz";
                continue; // continue doesn't matters: we're still iterating in loop
            }
            System.out.println("oops");
        }
    }

    void test3(String s, int flag) {
        for(int i=0; i<10; i++) {
            for(int j=0; j<10; j++) {
                if(flag == 0) {
                    s<warning descr="String concatenation '+=' in loop">+=</warning>"xyz";
                    break; // breaking inner loop only: still concatenation in loop
                }
            }
        }
    }

    void test4(String s, int flag) {
        for(int i=0; i<10; i++) {
            s = "x";
            for(int j=0; j<10; j++) {
                if(flag == 0) {
                    s+="xyz";
                    break; // breaking inner loop only, but variable is always reassigned in outer loop, so effectively concatenating once
                }
            }
            System.out.println(s);
        }
    }

    void testLabel(String s, int flag) {
        OUTER:
        for(int i=0; i<10; i++) {
            for(int j=0; j<10; j++) {
                if(flag == 0) {
                    s+="xyz";
                    break OUTER; // breaking outer loop: ok
                }
            }
        }
    }

    void testDefinedInLoop(List<?> list) {
        for(Object obj : list) {
            String s = "message";
            if(obj != null) {
                s+=obj; // replacing with StringBuilder won't change anything
            }
            System.out.println(s);
        }
    }

    public void testSwitch(int flag) {
        String s = "";
        for(int i=0; i<10; i++) {
            if(i > 5) {
                if(s != null) {
                    switch(flag) {
                        case 0:
                            s += "xyz";
                            break; // break switch, then break loop: effectively single concatenation
                        case 1:
                            s <warning descr="String concatenation '+=' in loop">+=</warning> "abc"; // fall-through
                        case 2:
                            s <warning descr="String concatenation '+=' in loop">+=</warning> "efg"; // continue loop: possibly multiple concatenations
                            continue;
                    }
                }
                break;
            }

        }
        System.out.println(s);
    }

    private boolean bar()
    {
        return true;
    }

    private String baz(String s)
    {
        return s;
    }

    public void oper() {
        final String[] array = new String[] { "a", "a", "a" };
        String s = "asdf";
        final int len =  array.length;
        for (int k = 0; k < len; k++) {
            array[k] += "b";
            s <warning descr="String concatenation '+=' in loop">+=</warning> k;
        }
    }

    void bla() {
        while (true) {
            System.out.println("a" + "b" + "c");
        }
    }

    String field;
    StringConcatenationInLoop parent;

    void fieldTest() {
        for(int i=0; i<10; i++) {
            field = this.field <warning descr="String concatenation '+' in loop">+</warning> "x";
        }
        for(int i=0; i<10; i++) {
            this.field = field <warning descr="String concatenation '+' in loop">+</warning> "x";
        }
        for(int i=0; i<10; i++) {
            this.field = this.field <warning descr="String concatenation '+' in loop">+</warning> "x";
        }
        for(int i=0; i<10; i++) {
            this.field = parent.field + "x";
        }
        for(int i=0; i<10; i++) {
            parent.field = this.field + "x";
        }
        for(int i=0; i<10; i++) {
            parent.field = this.parent.field <warning descr="String concatenation '+' in loop">+</warning> "x";
        }
    }

    void testUsedCompletely() {
        String s = "";
        for(int i=0; i<10; i++) {
            s+=i;
            System.out.println(i > 5 ? s : "...");
        }
    }
    static class C {
        String s = "";

        // IDEA-250202
        public static void main(String[] args) {
            String s = "";
            C c1;
            C c2 = new C();
            for (int i = 0; i < 100; i++) {
                s <warning descr="String concatenation '+=' in loop">+=</warning> "x";
                C c = new C();
                c.s += "p";
                c1 = new C();
                c1.s += "x";
                c2.s <warning descr="String concatenation '+=' in loop">+=</warning> "y";
            }
            System.out.println(s);
        }
    }
}