class Test {

  public void method1()
  {
    System.out.println(((1)));
    ;;
    System.out.println(2);
  }

  public void method2()
  {
      NewMethodResult x = newMethod();
  }

    NewMethodResult newMethod() {
        System.out.println(1);
        System.out.println(2);
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}