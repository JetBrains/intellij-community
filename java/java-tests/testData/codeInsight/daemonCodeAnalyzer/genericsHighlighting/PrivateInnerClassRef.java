import java.util.Comparator;

public abstract class A implements <error descr="'A.B' has private access in 'A'">Comparator<A.B></error> {

  private static class B {
  }

  private interface I extends Comparator<I>{}
  private interface I1 extends Comparator<I>{}
}

//abstract class C implements error descr="'C.D' has private access in 'C'">C.D error {
//  private static class D {}
//}
