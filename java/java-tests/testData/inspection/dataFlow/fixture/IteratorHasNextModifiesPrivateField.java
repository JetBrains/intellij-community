import java.util.*;

class Test implements Iterator<String> {
  String myNext;

  public boolean hasNext() {
    myNext = "test";
    return true;
  }

  public String next() {
    if (!hasNext()) throw new NoSuchElementException();
    return myNext;
  }

  void test() {
    int merged = 0;
    while (hasNext()) {
      assert myNext != null;
      merged++;
      if (<warning descr="Condition 'merged > 50' is always 'false'">merged > 50</warning>) {
        break;
      }
      myNext = <warning descr="Assigning 'null' value to non-annotated field">null</warning>;
    }
  }
}
