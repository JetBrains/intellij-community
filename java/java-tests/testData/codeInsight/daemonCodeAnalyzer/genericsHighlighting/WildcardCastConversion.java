public class Test {
  interface A {}
  interface B {}

 //? extends A, ? extends B -----------------------------------------
    void testEE1() {
        class A {}
        class B {}

        W<? extends A> xx = null;
        W<? extends B> y = <error descr="Inconvertible types; cannot cast 'W<capture<? extends A>>' to 'W<? extends B>'">(W<? extends B>) xx</error>;
    }

    void testEE2() {
        class A {}
        class B extends A {}

        W<? extends A> xx = null;
        W<? extends B> y = (W<? extends B>) xx;
    }

    void testEE21() {
        class A {}
        class B extends A {}

        W<? extends B> xx = null;
        W<? extends A> y = (W<? extends A>) xx;
    }

    void testEE211() {
        class A {}
        final class B extends A {}

        W<? extends A> xx = null;
        W<? extends B> y = (W<? extends B>) xx;
    }

    void test3EE() {
        W<? extends A> xx = null;
        W<? extends B> y = (W<? extends B>) xx;
    }

    void test4EE() {
        class A {}
        W<? extends A> xx = null;
        W<? extends B> y = (W<? extends B>) xx;
    }

    void test41EE() {
        class A {}
        W<? extends B> xx = null;
        W<? extends A> y = (W<? extends A>) xx;
    }

    void test411EE() {
        final class A {}
        W<? extends B> xx = null;
        W<? extends A> y = <error descr="Inconvertible types; cannot cast 'W<capture<? extends Test.B>>' to 'W<? extends A>'">(W<? extends A>) xx</error>;
    }

    void test412EE() {
        final class A implements B {}
        W<? extends B> xx = null;
        W<? extends A> y = (W<? extends A>) xx;
    }

    void test1() {
        class A {}
        class B {}

        W<? super A> xx = null;
        W<? super B> y = (W<? super B>) xx;
    }

    void test2() {
        final class A {}
        final class B {}

        W<? super A> xx = null;
        W<? super B> y = (W<? super B>) xx;
    }

   //? super A, ? super B -------------------------
    void test3SS() {
        W<? super A> xx = null;
        W<? super B> y = (W<? super B>) xx;
    }

  //? extends A, ? super B -------------------------
    void test1ES() {
        class A {}
        class B {}

        W<? extends A> x = null;
        W<? super B> y = <error descr="Inconvertible types; cannot cast 'W<capture<? extends A>>' to 'W<? super B>'">(W<? super B>) x</error>;
    }

    void test2ES() {
        class A {}
        class B extends A {}

        W<? extends A> x = null;
        W<? super B> y = (W<? super B>) x;
    }

    void test3ES() {
        class A {}
        class B extends A {}

        W<? extends B> x = null;
        W<? super A> y = <error descr="Inconvertible types; cannot cast 'W<capture<? extends B>>' to 'W<? super A>'">(W<? super A>) x</error>;
    }


    void test4ES() {
        W<? extends B> x = null;
        W<? super A> y = <error descr="Inconvertible types; cannot cast 'W<capture<? extends Test.B>>' to 'W<? super Test.A>'">(W<? super A>) x</error>;
    }

    void test5ES() {
        final class B implements A {}

        W<? extends A> x = null;
        W<? super B> y = (W<? super B>) x;
    }

    void test6ES() {
        final class B implements A {}

        W<? extends B> x = null;
        W<? super A> y = <error descr="Inconvertible types; cannot cast 'W<capture<? extends B>>' to 'W<? super Test.A>'">(W<? super A>) x</error>;
    }

  // ? extends A, B -----------------------
    void test1EWC() {
        class A {
        }
        class B {
        }

        W<? extends A> xx = null;
        W<B> y = <error descr="Inconvertible types; cannot cast 'W<capture<? extends A>>' to 'W<B>'">(W<B>) xx</error>;
    }

    void test2EWC() {
        class A {
        }

        W<? extends A> xx = null;
        W<?> y = (W<?>) xx;
    }

    void test3EWC() {
        class A {
        }
        class B extends A {
        }

        W<? extends A> xx = null;
        W<B> y = (W<B>) xx;
    }

  // ? super A, B -----------------------
    void test1SWC() {
        class A {
        }
        class B {
        }

        W<? super A> xx = null;
        W<B> y = <error descr="Inconvertible types; cannot cast 'W<capture<? super A>>' to 'W<B>'">(W<B>) xx</error>;
    }

    void test2SWC() {
        class A {
        }

        W<? super A> xx = null;
        W<?> y = (W<?>) xx;
    }

    void test3SWC() {
        class A {
        }
        class B extends A {
        }

        W<? super B> xx = null;
        W<A> y = (W<A>) xx;
    }

  // ?, ? ------------------------------------------
  void test1WWW() {
    W<?> xx = null;
    W<?> y = xx;
  }

  //? extends P<? extends A>, B --------------------

    void test1EEWC() {
        class A {
        }
        class B {
        }

        W<? extends P<? extends A>> xx = null;
        W<? extends B> y = <error descr="Inconvertible types; cannot cast 'W<capture<? extends P<? extends A>>>' to 'W<? extends B>'">(W<? extends B>) xx</error>;
    }

    void test2EEWC() {
        class A {
        }
        class B extends P {
        }

        W<? extends P<? extends A>> xx = null;
        W<? extends B> y = (W<? extends B>) xx;
    }

     void test3EEWC() {
        class A {
        }
        class B<TB> extends P<TB> {
        }

        W<? extends P<? extends A>> xx = null;
        W<? extends B<? super A>> y = (W<? extends B<? super A>>) xx;
    }


     void test4EEWC() {
        class A {
        }
        class B<TB> extends P<TB> {
        }

        W<? extends P<? extends A>> xx = null;
        W<? extends B<?>> y = (W<? extends B<?>>) xx;
    }

    void test5EEWC() {
        class A {
        }
        class B<TB> extends P<TB> {
        }
        class C {}

        W<? extends P<? extends A>> xx = null;
        W<? extends B<? extends C>> y = (W<? extends B<? extends C>>) xx;
    }

  //Array Types inside wildcards
  void test1AE() {
        class A {}
        class B {}

        W<? extends A[]> xx = null;
        W<? extends B[]> y = <error descr="Inconvertible types; cannot cast 'W<capture<? extends A[]>>' to 'W<? extends B[]>'">(W<? extends B[]>) xx</error>;
    }

    void test11AE() {
        class A {}
        class B {}

        W<? extends A[]> xx = null;
        W<? extends B> y = <error descr="Inconvertible types; cannot cast 'W<capture<? extends A[]>>' to 'W<? extends B>'">(W<? extends B>) xx</error>;
    }

     void test2AE() {
        class A {}
        class B extends A {}

        W<? extends A[]> xx = null;
        W<? extends B[]> y = (W<? extends B[]>) xx;
    }

    void test21AE() {
        class A {}
        class B extends A {}

        W<? extends A[]> xx = null;
        W<? extends B> y = <error descr="Inconvertible types; cannot cast 'W<capture<? extends A[]>>' to 'W<? extends B>'">(W<? extends B>) xx</error>;
    }



    void testIntAE() {

        W<? extends A[]> xx = null;
        W<? extends B[]> y = (W<? extends B[]>) xx;
    }

    void testInt1AE() {

        W<? extends A[]> xx = null;
        W<? extends B[][]> y = <error descr="Inconvertible types; cannot cast 'W<capture<? extends Test.A[]>>' to 'W<? extends Test.B[][]>'">(W<? extends B[][]>) xx</error>;
    }

    void testASS() {
        class A {}
        class B {}

        W<? super A[]> xx = null;
        W<? super B[]> y = (W<? super B[]>) xx;
    }

    void test1AES() {
        class A {}
        class B {}

        W<? extends A[]> x = null;
        W<? super B[]> y = <error descr="Inconvertible types; cannot cast 'W<capture<? extends A[]>>' to 'W<? super B[]>'">(W<? super B[]>) x</error>;
    }

    void test2AES() {
        class A {}
        class B extends A {}

        W<? extends A[]> x = null;
        W<? super B[]> y = (W<? super B[]>) x;
    }

    void test3AES() {
        class A {}
        class B extends A {}

        W<? extends B[]> x = null;
        W<? super A[]> y = <error descr="Inconvertible types; cannot cast 'W<capture<? extends B[]>>' to 'W<? super A[]>'">(W<? super A[]>) x</error>;
    }



    void test4AES() {
        W<? extends B[]> x = null;
        W<? super A[]> y = <error descr="Inconvertible types; cannot cast 'W<capture<? extends Test.B[]>>' to 'W<? super Test.A[]>'">(W<? super A[]>) x</error>;
    }

    void test5AES() {
        final class B implements A {}

        W<? extends A[]> x = null;
        W<? super B[]> y = (W<? super B[]>) x;
    }

    void test6AES() {
        final class B implements A {}

        W<? extends B[]> x = null;
        W<? super A[]> y = <error descr="Inconvertible types; cannot cast 'W<capture<? extends B[]>>' to 'W<? super Test.A[]>'">(W<? super A[]>) x</error>;
    }

  // type parameters extensions: D<T extends A>
  void testT3() {
        class A {
        }
        class D<T extends A > {
            class B extends A {
            }

            void foo() {
                D<? extends T> x = null;
                D<? extends B> y = (D<? extends B>) x;
            }

        }

    }

    void testT4() {
        class A {
        }
        class D<T extends A> {
            class B {
            }

            void foo() {
                D<? extends T> x = null;
                D<<error descr="Type parameter '? extends B' is not within its bound; should extend 'A'">? extends B</error>> y = (D<<error descr="Type parameter '? extends B' is not within its bound; should extend 'A'">? extends B</error>>) x;
            }

        }

    }


    void testT5() {
           class D<T> {
               class B {
               }

               void foo() {
                   D<? extends T> x = null;
                   D<? extends B> y = (D<? extends B>) x;
               }

           }

       }

    void testT6() {
        class A {
        }
        class D<T extends A> {
            class B extends A {
            }

            void foo() {
                D<? super T> x = null;
                D<? super B> y = (D<?  super B>) x;
            }

        }

    }

     void testT7() {
        class A {
        }
        class D<T extends A> {
            class B extends A {
            }

            void foo() {
                D<? extends T> x = null;
                D<? super B> y = (D<?  super B>) x;
            }

        }

    }

     void testT8() {
        class A {
        }
        class D<T extends A> {
            class B extends A {
            }

            void foo() {
                D<? super T> x = null;
                D<? extends B> y = (D<? extends B>) x;
            }

        }

    }

     void testT9() {
        class A {
        }
        class D<T> {
            class B extends A {
            }

            void foo() {
                D<? super T> x = null;
                D<? extends A> y = (D<? extends B>) x;
            }

        }

    }

    void testUnbounded() {
      W<?> x = null;
      W<? extends A> y = ( W<? extends A>) x;
      W<?> y1 = (W<?>)x;
    }
}

class W<T> {}
class P<L> {}