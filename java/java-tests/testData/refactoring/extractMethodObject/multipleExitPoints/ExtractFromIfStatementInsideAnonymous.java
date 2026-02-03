class Test {
  void foo() {
    new Runnable() {
      public void run() {
          <selection>String var = null;
          if (var == null) {
            return;
          }</selection>
          System.out.println(var);
      }
    };
  }
}