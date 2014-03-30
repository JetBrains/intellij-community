import java.util.Comparator;

abstract class A implements <error descr="'A.B' has private access in 'A'">Comparator<A.B></error> {

  private static class B {
  }

  private interface I extends Comparator<I>{}
  private interface I1 extends Comparator<I>{}
}

//abstract class C implements error descr="'C.D' has private access in 'C'">C.D error {
//  private static class D {}
//}

class JSReferenceSet {
    static class MyResolver implements JSResolveUtil.Resolver<M> {}
    class M extends JSResolveUtil.F {}
}
class JSResolveUtil {
    static interface Resolver<T extends F> {}
    static class F {}
}


class TestIDEA62515 {
  public static interface Model<T> {}
  public class Inner {}
  public static class Foo implements Model<Inner> {}
}