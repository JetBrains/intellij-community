class Example {
  public static void main(String[] args) {
    final Runnable r = new Runnable() {
      public void run() {}
      public void m2() {}
    };
    r.<error descr="Cannot resolve method 'm2' in 'Runnable'">m2</error>();
  }
}