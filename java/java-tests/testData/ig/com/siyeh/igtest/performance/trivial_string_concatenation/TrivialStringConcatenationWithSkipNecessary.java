package com.siyeh.igtest.performance.trivial_string_concatenation;

public class TrivialStringConcatenationWithSkipNecessary {

    public void foo() {
        String constant = "" + 10;
        final String foo = "" + 4 + <warning descr="Empty string in concatenation">""</warning> + 3;
        String bar = <warning descr="Empty string in concatenation">""</warning> + new Integer(4) + "asdf";
        Float aFloat = new Float(3.0);
        String baz = "" + aFloat;

        String trivial = <warning descr="Empty string in concatenation">""</warning> + " ";
        String doubleConstant = "foo" + (<warning descr="Empty string in concatenation">""</warning>) + (<warning descr="Empty string in concatenation">""</warning>);
    }

  private static void test(int x, String y) {
    String s = "" + x;
    System.out.println(s);
    s = x + "";
    System.out.println(s);
    s = <warning descr="Empty string in concatenation">""</warning> + y; //warn
    System.out.println(s);
    s = y + <warning descr="Empty string in concatenation">""</warning>;  //warn
    System.out.println(s);
    s = <warning descr="Empty string in concatenation">""</warning> + x + y;  //warn
    System.out.println(s);
    s = x + <warning descr="Empty string in concatenation">""</warning> + y;  //warn
    System.out.println(s);
    s = y + <warning descr="Empty string in concatenation">""</warning> + x;  //warn
    System.out.println(s);
    s = x + "" + x;
    System.out.println(s);
    s = y + <warning descr="Empty string in concatenation">""</warning> + y;  //warn
    System.out.println(s);
    s = y + x + <warning descr="Empty string in concatenation">""</warning>;  //warn
    System.out.println(s);
    s = x + x + "";
    System.out.println(s);
    s = <warning descr="Empty string in concatenation">""</warning> + "x";   //warn
    System.out.println(s);
    s = "" + x + <warning descr="Empty string in concatenation">""</warning>;  //warn
    System.out.println(s);
    s = "" + x + <warning descr="Empty string in concatenation">""</warning> + 1;  //warn
    System.out.println(s);
    s = 1 + "" + x + <warning descr="Empty string in concatenation">""</warning> + 1;  //warn
    System.out.println(s);
    s = "" + 1 + <warning descr="Empty string in concatenation">""</warning> + x + <warning descr="Empty string in concatenation">""</warning> + 1;  //warn
    System.out.println(s);
  }
}
