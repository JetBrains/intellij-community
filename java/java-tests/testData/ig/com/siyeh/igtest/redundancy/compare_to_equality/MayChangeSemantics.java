class Exigent implements Comparable<Exigent> {
  @Override
  public int compareTo(Exigent o) {
    return 0;
  }

  void test(Exigent other) {
    if (<caret>compareTo(other) == 0) {
      System.out.println("equal");
    }
  }
}