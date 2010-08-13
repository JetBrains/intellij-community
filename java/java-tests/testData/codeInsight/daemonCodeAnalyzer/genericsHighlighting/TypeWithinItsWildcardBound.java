public class WithingBounds {
    interface I {
    }

    void testE1() {
        class A {
        }
        class B extends A {
        }
        class ToCheckExtends<TTT extends A> {
        }

        ToCheckExtends<? extends B> pr;
    }

    void testERec1() {
        class A {
        }
        class B<K> extends A {
        }
        class C<Y>{}
        class ToCheckExtends<TTT extends A> {
        }

        ToCheckExtends<<error descr="Type parameter 'C' is not within its bound; should extend 'A'">?  extends C<? extends C></error>> pr;
        ToCheckExtends<? extends B<? extends C>> pr1;
    }


    void testE2() {
        class A {
        }
        class B {
        }
        class ToCheckExtends<TTT extends A> {
        }

        ToCheckExtends<<error descr="Type parameter 'B' is not within its bound; should extend 'A'">? extends B</error>> pr;
    }

    void testE22() {
        class B {
        }
        class A extends B {
        }
        class ToCheckExtends<TTT extends A> {
        }

        ToCheckExtends<? extends B> pr;
    }


    void testE23() {
        class A {
        }
        class ToCheckExtends<TTT extends A> {
        }

        ToCheckExtends<? extends I> pr;
    }

    void testE24() {
        final class A {
        }
        class ToCheckExtends<TTT extends A> {
        }

        ToCheckExtends<<error descr="Type parameter 'WithingBounds.I' is not within its bound; should implement 'A'">? extends I</error>> pr;
    }


    //---------------------------------

    void testE3() {
        class A {
        }
        class ToCheckExtends<TTT extends I> {
        }

        ToCheckExtends<? extends A> pr;
    }

    void testE4() {
        final class A {
        }
        class ToCheckExtends<TTT extends I> {
        }

        ToCheckExtends<<error descr="Type parameter 'A' is not within its bound; should implement 'WithingBounds.I'">? extends A</error>> pr;
    }

    void testE5() {
        final class A implements I {
        }
        class ToCheckExtends<TTT extends I> {
        }

        ToCheckExtends<? extends A> pr;
    }


    interface AInterface {
    }
    void testE6() {

        class ToCheckExtends<TTT extends I> {
        }

        ToCheckExtends<? extends AInterface> pr;
    }

    //-----------------------------

    void testS1() {
        class A {
        }
        class B extends A {
        }
        class ToCheckExtends<TTT extends A> {
        }

        ToCheckExtends<? super B> pr;
    }

    void testS2() {
        class A {
        }
        class B {
        }
        class ToCheckExtends<TTT extends A> {
        }

        ToCheckExtends<<error descr="Type parameter 'B' is not within its bound; should extend 'A'">? super B</error>> pr;
    }

    void testS3() {
        class A {
        }
        class B extends A {
        }
        class ToCheckExtends<TTT extends B> {
        }

        ToCheckExtends<<error descr="Type parameter 'A' is not within its bound; should extend 'B'">? super A</error>> pr;
    }
}