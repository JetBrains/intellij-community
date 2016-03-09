import java.util.*;

class X<A extends Comparable<A>, B extends Comparable<B>> {

  class Pair implements Comparable<Pair> {
    A a;
    B b;


    public A getA() {
      return a;
    }


    public B getB() {
      return b;
    }


    @Override
    public int compareTo(Pair other) {
      Comparator<Pair> comparator =  Comparator.comparing(Pair::getA).thenComparing(Pair::getB);

      return comparator.compare(this, other);
    }
  }

}