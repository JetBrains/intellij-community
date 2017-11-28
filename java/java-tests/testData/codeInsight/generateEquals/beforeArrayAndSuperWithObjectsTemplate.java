class I {
  public int hashCode() {
    return 0;
  }
  public boolean equals(Object o) {
    return o == this;
  }
}
class A extends I {
  int[] a1;
  
  <caret>
}