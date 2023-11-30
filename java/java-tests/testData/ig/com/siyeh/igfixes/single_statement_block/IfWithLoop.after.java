class X {
  void f(int[] a) {
    if (a.length != 0) for (int i : a)
        System.out.println(i);
    else {
      System.out.println("no");
    }
  }
}