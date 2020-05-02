class Test {
  String[] foos;

  void test() {
    for (String foo : newMethod()) {

    }
    System.out.println(newMethod().length);
  }

    private String[] newMethod() {
        return foos;
    }
}