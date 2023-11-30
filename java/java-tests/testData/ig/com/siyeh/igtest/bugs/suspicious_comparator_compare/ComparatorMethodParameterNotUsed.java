import java.util.*;

class ComparatorMethodParameterNotUsed implements Comparator<String> {

  Comparator<Integer> c = (<warning descr="'compare()' parameter 'a' is not used">a</warning>, <warning descr="'compare()' parameter 'b' is not used">b</warning>) -> new Random().nextInt();
  Comparator<Integer> d = (a, b) -> { throw null; };
  Comparator<Integer> zero1 = (a, b) -> 0;
  Comparator<Integer> zero2 = (a, b) -> { return 0; };
  Comparator<Integer> zero3 = new Comparator<Integer>() {
    public int compare(Integer i1, Integer i2) {
      return 0;
    }
  };

  public int compare(String <warning descr="'compare()' parameter 's1' is not used">s1</warning>, String <warning descr="'compare()' parameter 's2' is not used">s2</warning>) {
    return new Random().nextInt();
  }
}
class Comparator2 implements Comparator<Comparator2> {

  public int compare(Comparator2 c1, Comparator2 c2) {
    throw new UnsupportedOperationException();
  }
}
class IncompleteComparator implements Comparator<Boolean> {

  public int compare(Boolean b1, Boolean b2)<EOLError descr="'{' or ';' expected"></EOLError>
}