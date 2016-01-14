class A {

    String myFoo;
}

class B {
    String myFoo;

    public static void foo(A a) {
        System.out.println(myFoo);
        System.out.println(a.myFoo);
    }
}