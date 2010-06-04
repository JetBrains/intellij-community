class Test {
  void foo() {
    <selection>final String str = "";
    if (str == "") {
      return;
    }</selection>
    new Runnable() {
      public void run() {
        System.out.println(str);
      }
    }
  }
}