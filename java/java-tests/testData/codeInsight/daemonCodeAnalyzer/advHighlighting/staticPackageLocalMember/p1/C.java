package p1;

import p2.B;

public class C extends B {
    void f(){
        System.out.println(<error descr="'FOO' is not public in 'p1.A'. Cannot be accessed from outside package">FOO</error>);
        <error descr="'foo()' is not public in 'p1.A'. Cannot be accessed from outside package">foo</error>();

        System.out.println(A.FOO);
        A.foo();

        System.out.println(B.<error descr="'FOO' is not public in 'p1.A'. Cannot be accessed from outside package">FOO</error>);
        B.<error descr="'foo()' is not public in 'p1.A'. Cannot be accessed from outside package">foo</error>();
    }
}
