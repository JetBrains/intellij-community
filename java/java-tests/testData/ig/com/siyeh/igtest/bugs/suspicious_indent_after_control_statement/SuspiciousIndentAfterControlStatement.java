package com.siyeh.igtest.bugs.suspicious_indent_after_control_statement;

import java.util.*;

public class SuspiciousIndentAfterControlStatement {

    public static String suspicousIndent(int i, int j) {
        switch (i) {
            case 1:
                if (j  / 8 == 0)
                    return "x";
<warning descr="Suspicious indentation after 'if' statement">                    </warning>case 0:
                if (j % 2 == 0)
                    return "even";
                else
                    return "odd";
                // with a break; the next line is okay, without it it gets the warning "suspicous indentation"
            default:
                throw new IllegalArgumentException("unknown function " + i);
        }
    }

    void indent(int i) {
        if (i ==9)
            System.out.println("foo");
<warning descr="Suspicious indentation after 'if' statement">            </warning>System.out.println("bar");
        if (i == 10);
<warning descr="Suspicious indentation after 'if' statement">            </warning>System.out.println("great");

    }

    public int get(Class baseClass) {
        // "interesting" parse tree.
        if (baseClass.equals(<error descr="',' or ')' expected"><error descr="Expression expected">.</error></error><error descr="')' expected"><error descr="Identifier expected">c</error></error>lass<error descr="Identifier expected"><error descr="Unexpected token">)</error></error><error descr="Unexpected token">)</error> {
            return 1;
        }
        return 0;
    }

    void m() {
        if (true)
            System.out.println();
<warning descr="Suspicious indentation after 'if' statement">            </warning>class<error descr="Identifier expected"> </error> ;
    }

    class Lol {
        public void someMethod(String a, String b, Boolean c)
        {
            List<Integer> list = new ArrayList<>();
            if(a != null) {
                list.add(5);
            }
            else
            if (b != null) {
                list.add(4);
                if (c) {
                    list.add(3);
                }
            }
            else {
                list.add(2);
            }
            list.add(1);
            if (list != null) {
                list.add(0);
            }
        }
    }

    public String switchStatement(int test) {
        int random = new Random().nextInt();
        switch (test) {
            case 0:
                if (random < 0) return "0";
            case 1:
                if (random < 0) {
                    return "-1";
                } else if (random > 0) {
                    return "+1";
                } else {
                    return null;
                }
            case 2:  // <--- IDEA-148562 inspection error on this line
            default:
                return "";
        }
    }

    public void x(int i) {
        if (i == 3)
<warning descr="Suspicious indentation after 'if' statement">	    </warning>System.out.println("-->");
        System.out.println(i);
    }

    public void y(int i) {
        if (i == 42)
            System.out.println("answer");
<warning descr="Suspicious indentation after 'if' statement">                </warning>System.out.println("question");
    }

    public void z(int i) {
if (i == 99)
<warning descr="Suspicious indentation after 'if' statement"></warning>System.out.println("problems");
    }
}