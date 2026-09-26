class A {
    static {
        c = "c";
    }
    static String b = a = "";
    static String d = <error descr="Final field 'c' is already initialized in a class initializer">c</error> = "";

    static {
         <error descr="Final field 'a' is already initialized in another field initializer">a</error> = "";
    }
    static final String a;
    static final String c;
}
class B {
    {
        c = "c";
    }
    String b = a = "";
    String d = <error descr="Final field 'c' is already initialized in a class initializer">c</error> = "";

    {
         <error descr="Final field 'a' is already initialized in another field initializer">a</error> = "";
    }
    final String a;
    final String c;
}