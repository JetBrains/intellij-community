import java.util.List;

class Test {
  public <A> void foo(List<?> list, List<? extends Object> list2) {
    Comparable<A> <warning descr="Variable 'c1' is never used">c1</warning> = <warning descr="Unchecked cast: 'capture<?>' to 'java.lang.Comparable<A>'">(Comparable<A>)list.get(0)</warning>;
    Comparable<A> <warning descr="Variable 'c2' is never used">c2</warning> = <warning descr="Unchecked cast: 'capture<? extends java.lang.Object>' to 'java.lang.Comparable<A>'">(Comparable<A>)list2.get(0)</warning>;
  }
}