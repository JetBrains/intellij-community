import java.util.*;

class Test {

  public static <R, E, RC extends Collection<R>, C extends Collection<E>> RC collectionGenericTest(C collection, Lambda<R, E> lambda) {
    return (RC) new Vector<R>();
  }

  public static <R, E, RC extends List<R>, C extends List<E>> RC listGenericTest(C list, Lambda<R, E> lambda) {
    return (RC) new Vector<R>();
  }

  public static void testGeneric() {
    Collection<String> testCollection = collectionGenericTest(new Vector<Integer>(), new Lambda<String, Integer>() {
      @Override
      public String lambda(Integer l) {
        return null;
      }
    });

    List<String> testList = listGenericTest(new Vector<Integer>(), new Lambda<String, Integer>() {
      @Override
      public String lambda(Integer l) {
        return null;
      }
    });
  }

  private interface Lambda<R, A> {
    public R lambda(A l);
  }

  <error descr="Class 'Vector' must either be declared abstract or implement abstract method 'get(int)' in 'AbstractList'">private static class Vector<A> extends AbstractList<A> implements List<A></error> {
    public Vector() {
    }
  }
}
