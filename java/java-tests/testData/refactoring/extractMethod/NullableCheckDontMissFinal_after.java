class Test {
  void foo() {
      final String str = newMethod();
    new Runnable() {
      public void run() {
        System.out.println(str);
      }
    }
  }

    private String newMethod() {
        final String str = "";
        if (str == "") {
          return;
        }
        return str;
    }
}