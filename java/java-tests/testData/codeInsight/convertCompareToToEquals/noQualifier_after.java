class X implements Comparable<X> {
  boolean m(X x) {
    return equals(x);
  }

  public int compareTo(X x) {
    return 0;
  }
}