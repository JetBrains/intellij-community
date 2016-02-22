class A {
    public static void foo(A a) {
        System.out.println(myFoo);
        System.out.println(a.myFoo);
    }
    
    String myFoo;
}

class B {
    String myFoo;
}