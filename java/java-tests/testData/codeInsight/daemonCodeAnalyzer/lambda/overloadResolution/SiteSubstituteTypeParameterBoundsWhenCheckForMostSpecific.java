
import java.util.ArrayList;
import java.util.List;

class Test {

  public static class Foo<T> {
    public void set(Iterable<T> v) {
      System.out.println(v);
    }

    public <Y extends List<T>> void set(Y v) {
       System.out.println(v);
    }
  }

  static void main(Foo<Double> doubleFoo, ArrayList<Double> data) {
    doubleFoo.set(data);
  }

}
