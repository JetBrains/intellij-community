class Test {

  public static void main(String[] args) {
    multiBound("test");
    multiBound(null);
  }

  static <E extends Comparable<E> & CharSequence> void multiBound(E e) {}
}