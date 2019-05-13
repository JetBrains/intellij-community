class WithingBounds {
    interface I {
    }
    interface I1 {
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

        ToCheckExtends<<error descr="Type parameter '? extends C<? extends C>' is not within its bound; should extend 'A'">?  extends C<? extends C></error>> pr;
        ToCheckExtends<? extends B<? extends C>> pr1;
    }


    void testE2() {
        class A {
        }
        class B {
        }
        class ToCheckExtends<TTT extends A> {
        }

        ToCheckExtends<<error descr="Type parameter '? extends B' is not within its bound; should extend 'A'">? extends B</error>> pr;
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

        ToCheckExtends<<error descr="Type parameter '? extends I' is not within its bound; should extend 'A'">? extends I</error>> pr;
    }


    void testE25() {
      class B {
        void foo() {
          this.<Iterable>bar();
        }

        <T extends Iterable<String>> void bar() {
        }
      }
    }

    void testE26() {
      class A<T>{}
      class B {
        void foo() {
          this.<A>bar();
        }

        <T extends A<String>> void bar() {
        }
      }
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

        ToCheckExtends<<error descr="Type parameter '? extends A' is not within its bound; should extend 'WithingBounds.I'">? extends A</error>> pr;
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

        ToCheckExtends<<error descr="Type parameter '? super B' is not within its bound; should extend 'A'">? super B</error>> pr;
    }

    void testS3() {
        class A {
        }
        class B extends A {
        }
        class ToCheckExtends<TTT extends B> {
        }

        ToCheckExtends<<error descr="Type parameter '? super A' is not within its bound; should extend 'B'">? super A</error>> pr;
    }

  void testMisc() {
     class A<T, S extends T> {}
     class i {}
     final class ii extends i {}

     A<String, String> pr4;
     A<Integer, <error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Integer'">String</error>> pr5;
     A<String, ? extends String> pr51;
     A<Integer, <error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Integer'">? extends String</error>> pr52;
     A<String, ? super String> pr53;
     A<ii, <error descr="Type parameter 'i' is not within its bound; should extend 'ii'">i</error>> pr54;
     A<i, ii> pr55;
     A<i, ? extends ii> pr56;
     A<ii, ? extends i> pr57;
     A<i, ? super ii> pr58;
     A<ii, <error descr="Type parameter '? super i' is not within its bound; should extend 'ii'">? super i</error>> pr59;
     A<i, <error descr="Type parameter '? extends A<i, i>' is not within its bound; should extend 'i'">? extends A<i, i></error>> pr510;
     A<i, <error descr="Type parameter 'ii[]' is not within its bound; should extend 'i'">ii[]</error>> pr511;
     A<i, ?> pr512;

     A<?, ?> pr30;
     A<?, <error descr="Type parameter 'i' is not within its bound; should extend '?'">i</error>> pr3;
     A<? extends Object, i> pr330;
     A<?, ? extends ii> pr2;
     A<?, <error descr="Type parameter '? super String' is not within its bound; should extend '?'">? super String</error>> pr10;
     A<?, <error descr="Type parameter 'A' is not within its bound; should extend '?'">A<?, ?></error>> pr31;
     A<?, <error descr="Type parameter 'ii[]' is not within its bound; should extend '?'">ii[]</error>> pr32;
     A<?, <error descr="Type parameter 'A' is not within its bound; should extend '?'">A<i, i></error>> pr33;

     A<? extends i, i> pr6;
     A<? extends ii, <error descr="Type parameter 'i' is not within its bound; should extend '? extends ii'">i</error>> pr6x1;
     A<? extends i, ii> pr6x2;
     A<? extends ii, ii> pr6x3;
     A<? extends i, <error descr="Type parameter 'java.lang.Integer' is not within its bound; should extend '? extends i'">Integer</error>> pr8;

     A<? extends i, <error descr="Type parameter '? extends String' is not within its bound; should extend '? extends i'">? extends String</error>> pr12;
     A<? extends i, ? extends i> pr13;
     A<? extends i, ? extends ii> pr14;
     A<? extends ii, ? extends i> pr13x3;
     A<? extends ii, ? extends ii> pr13x4;


     A<? extends i, ? super i> pr19;
     A<? extends i, ? super ii> pr110;
     A<? extends ii, ? super ii> pr11x0;
     A<? extends ii, <error descr="Type parameter '? super i' is not within its bound; should extend '? extends ii'">? super i</error>> pr111;

     A<? extends i, ?> pr15;
     A<? extends i, <error descr="Type parameter 'ii[]' is not within its bound; should extend '? extends i'">ii[]</error>> pr16;
     A<? extends i, <error descr="Type parameter 'A' is not within its bound; should extend '? extends i'">A<?, ?></error>> pr17;
     A<? extends i, <error descr="Type parameter 'A' is not within its bound; should extend '? extends i'">A<ii, ii></error>> pr18;
     A<? extends ii, <error descr="Type parameter '? super String' is not within its bound; should extend '? extends ii'">? super String</error>> pr112;
     A<? extends ii, <error descr="Type parameter '? super A<i, i>' is not within its bound; should extend '? extends ii'">? super A<i, i></error>> pr113;

     A<? super i, i> pr701;
     A<? super i, <error descr="Type parameter 'java.lang.String' is not within its bound; should extend '? super i'">String</error>> pr72;
     A<? super i, ?> pr73;
     A<? super i, <error descr="Type parameter 'ii[]' is not within its bound; should extend '? super i'">ii[]</error>> pr74;
     A<? super i, <error descr="Type parameter '? extends String' is not within its bound; should extend '? super i'">? extends String</error>> pr75;
     A<? super i, ? extends i> pr76;
     A<? super ii, ? extends i> pr77;
     A<? super ii, ? extends ii> pr78;
     A<? super i, ? super i> pr79;
     A<? super i, ? super ii> pr791;
     A<? super ii, ? super ii> pr713;
     A<? super i, <error descr="Type parameter '? super String' is not within its bound; should extend '? super i'">? super String</error>> pr712;
     A<? super ii, <error descr="Type parameter '? super i' is not within its bound; should extend '? super ii'">? super i</error>> pr710;
     A<? super  ii, <error descr="Type parameter 'i' is not within its bound; should extend '? super ii'">i</error>> pr70;
     A<? super i, ii> pr71;
     A<? super i, ? extends ii> pr711;

     A<i[], i[]> a1;
     A<i[], <error descr="Type parameter 'java.lang.Object' is not within its bound; should extend 'i[]'">Object</error>> a2;
     A<i[], ii[]> a3;
     A<ii[], <error descr="Type parameter 'i[]' is not within its bound; should extend 'ii[]'">i[]</error>> a4;

     A<i[], <error descr="Type parameter 'int[]' is not within its bound; should extend 'i[]'">int[]</error>> a5;
     A<Object, int[]> a6;
     A<Cloneable, int[]> a7;
     A<java.io.Serializable, int[]> a8;
     A<Cloneable[], <error descr="Type parameter 'int[]' is not within its bound; should extend 'java.lang.Cloneable[]'">int[]</error>> a9;
     A<Cloneable[], int[][]> a10;
     A<Cloneable[][], int[][][]> a11;
     A<Cloneable[], <error descr="Type parameter 'i[]' is not within its bound; should extend 'java.lang.Cloneable[]'">i[]</error>> a12;
     A<Cloneable[], i[][]> a13;

     A<? super i[], ii[]> a14;
     A<? super i[], i[]> a140;
     A<? super ii[], <error descr="Type parameter 'i[]' is not within its bound; should extend '? super ii[]'">i[]</error>> a141;
     A<? super i[], <error descr="Type parameter 'i' is not within its bound; should extend '? super i[]'">i</error>> a142;
     A<? super i[], <error descr="Type parameter 'java.lang.String' is not within its bound; should extend '? super i[]'">String</error>> a143;
     A<? super i[], <error descr="Type parameter 'i[][]' is not within its bound; should extend '? super i[]'">i[][]</error>> a144;
     A<? super i[], ? extends i[]> a145;
     A<? super i[], ? extends ii[]> a146;
     A<? super ii[], ? extends i[]> a147;
     A<? super i[], <error descr="Type parameter '? extends i' is not within its bound; should extend '? super i[]'">? extends i</error>> a148;
     A<? super i[], <error descr="Type parameter '? extends String' is not within its bound; should extend '? super i[]'">? extends String</error>> a149;
     A<? super i[], ?> a1410;
     A<? super i[], <error descr="Type parameter '? extends i[][]' is not within its bound; should extend '? super i[]'">? extends i[][]</error>> a1411;
     A<? super i[], ? super i[]> a1412;
     A<? super i[], ? super ii[]> a1413;
     A<? super ii[], <error descr="Type parameter '? super i[]' is not within its bound; should extend '? super ii[]'">? super i[]</error>> a1414;
     A<? super i[], <error descr="Type parameter '? super i' is not within its bound; should extend '? super i[]'">? super i</error>> a1415;
     A<? super i[], <error descr="Type parameter '? super String' is not within its bound; should extend '? super i[]'">? super String</error>> a1416;
     A<? super i[], <error descr="Type parameter '? super i[][]' is not within its bound; should extend '? super i[]'">? super i[][]</error>> a1417;

      A<? extends i[], ii[]> a15;
      A<? extends i[], i[]> a150;
      A<? extends ii[],<error descr="Type parameter 'i[]' is not within its bound; should extend '? extends ii[]'">i[]</error>> a151;
      A<? extends i[], <error descr="Type parameter 'i' is not within its bound; should extend '? extends i[]'">i</error>> a152;
      A<? extends i[], <error descr="Type parameter 'java.lang.String' is not within its bound; should extend '? extends i[]'">String</error>> a153;
      A<? extends i[], <error descr="Type parameter 'i[][]' is not within its bound; should extend '? extends i[]'">i[][]</error>> a154;
      A<? extends i[], ? extends i[]> a155;
      A<? extends i[], ? extends ii[]> a156;
      A<? extends ii[], ? extends i[]> a157;
      A<? extends i[], <error descr="Type parameter '? extends i' is not within its bound; should extend '? extends i[]'">? extends i</error>> a158;
      A<? extends i[], <error descr="Type parameter '? extends String' is not within its bound; should extend '? extends i[]'">? extends String</error>> a159;
      A<? extends i[], ?> a1510;
      A<? extends i[], <error descr="Type parameter '? extends i[][]' is not within its bound; should extend '? extends i[]'">? extends i[][]</error>> a1511;
      A<? extends i[], ? super i[]> a1512;
      A<? extends i[], ? super ii[]> a1513;
      A<? extends ii[], <error descr="Type parameter '? super i[]' is not within its bound; should extend '? extends ii[]'">? super i[]</error>> a1514;
      A<? extends i[], <error descr="Type parameter '? super i' is not within its bound; should extend '? extends i[]'">? super i</error>> a1515;
      A<? extends i[], <error descr="Type parameter '? super String' is not within its bound; should extend '? extends i[]'">? super String</error>> a1516;
      A<? extends i[], <error descr="Type parameter '? super i[][]' is not within its bound; should extend '? extends i[]'">? super i[][]</error>> a1517;

    A<? extends Cloneable, ? extends i[]> a16;
    A<? extends Cloneable[], ? extends i[][]> a160;
    A<Cloneable, ? extends i[]> a161;
    A<? super Cloneable, ? extends i[]> a162;
    A< I1[], ? extends  I[]> cl;

  }

  void testRawTypes() {
    class A<T extends A<T>> {}
    A a;
    A<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'A<java.lang.String>'">String</error>> a1;
    A<<error descr="Type parameter 'A' is not within its bound; should extend 'A<A>'">A</error>> a2;
    A<A<<error descr="Type parameter 'A' is not within its bound; should extend 'A<A>'">A</error>>> a3;

    A<? extends A> a4;
    A<<error descr="Type parameter '? super A' is not within its bound; should extend 'A<? super A>'">? super A</error>> a5;
    A<<error descr="Type parameter 'A[]' is not within its bound; should extend 'A<A[]>'">A[]</error>> a7;
  }
}
