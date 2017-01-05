class X implements Comparable<X> {
  boolean m(X x) {
    return <caret>compareTo(x) == ((int)0.0);
  }

  public int compareTo(X x) {
    return 0;
  }
}