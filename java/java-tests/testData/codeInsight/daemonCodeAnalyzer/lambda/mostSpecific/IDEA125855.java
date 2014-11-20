
import java.util.Collection;
import java.util.List;

class Test {

  private static <E> void <warning descr="Private method 'x(java.util.Collection<E>)' is never used">x</warning>(Collection<E> collection) {
    System.out.println(collection);
  }

  private static <E> void x(List<E> list) {
    System.out.println(list);
  }

  public static void main(List list) {
    Test.<Object[]>x(list);
  }
}
