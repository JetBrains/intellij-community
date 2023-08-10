public class FinalPrivateMethod {

  @SafeVarargs
  private <warning descr="'private' method declared 'final'">final</warning> <T> void x(T... ss) {}
}