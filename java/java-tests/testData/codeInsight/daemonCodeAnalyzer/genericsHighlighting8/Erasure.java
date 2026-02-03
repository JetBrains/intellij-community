import java.util.Collection;

abstract class NCollections {
  public <T> void foo(Collection<? extends T> coll) {
    bar((Collection)coll);
  }

  public abstract <T2 extends Object & Comparable<? super T2>> T2 bar(Collection<? extends T2> coll);
}