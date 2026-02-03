
import java.util.*;

import java.util.function.Predicate;

class MyTest<A> {
  private final Set<A> set;

  public MyTest() {
    set = (Set<A>) newSet();
  }

  private static <A extends Comparable<A>> Set<A> newSet() {
    return null;
  }

  static <T> Predicate<T> eq(Predicate<? super T> predicate) {
    return predicate::test;
  }

  static <T> void test(Predicate<? super T> predicate) {
    class X {
      <TT> void test2() {
        Predicate<? super TT> not = (Predicate<? super TT>)eq(predicate);
      }
    }
  }
}
class Repro {
  public interface MembersInjector<T> { }
  static <T> MembersInjector<T> create(Class<T> cls) { return null; }
  public static void main(String[] args) {
    Class<?>[] parameterTypes = new Class<?>[1];
    MembersInjector<Object> injector = (MembersInjector<Object>) create(parameterTypes[0]);
  }
}