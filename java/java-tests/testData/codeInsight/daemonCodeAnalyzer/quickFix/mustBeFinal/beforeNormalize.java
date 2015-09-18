// "Transform variables into final one element array" "true"

class aa {
  void f() {
    String a = "sdcfec", b = "sadwe";
    b = "s";
    a = a.substring(b.length());
    new Runnable() {
      @Override
      public void run() {
        a = "ss";
        b = 99 + a;
        System.out.println(a.substring(<caret>b.length()));
      }
    };
  }
}

