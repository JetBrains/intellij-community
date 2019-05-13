// "Transform variables into final one element array" "true"

class aa {
  void f() {
      final String[] a = {"sdcfec"};
      final String[] b = { "sadwe" };
      b[0] = "s";
    a[0] = a[0].substring(b[0].length());
    new Runnable() {
      @Override
      public void run() {
        a[0] = "ss";
        b[0] = 99 + a[0];
        System.out.println(a[0].substring(b[0].length()));
      }
    };
  }
}

