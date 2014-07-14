class Doo {

  void foo(Throwable e) {
    Throwable t = e;

    while (t.getCause() != null) t = t.getCause();
    if (e != t) {
      System.out.println();
    }
  }

}