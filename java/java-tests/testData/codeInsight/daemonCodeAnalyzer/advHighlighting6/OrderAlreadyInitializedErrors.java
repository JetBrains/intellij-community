class A {
    static {
        c = "c";
    }
    static String b = a = "";
    static String d = <error descr="Variable 'c' might already have been assigned to">c</error> = "";

    static {
         <error descr="Variable 'a' might already have been assigned to">a</error> = "";
    }
    static final String a;
    static final String c;
}
class B {
    {
        c = "c";
    }
    String b = a = "";
    String d = <error descr="Variable 'c' might already have been assigned to">c</error> = "";

    {
         <error descr="Variable 'a' might already have been assigned to">a</error> = "";
    }
    final String a;
    final String c;
}