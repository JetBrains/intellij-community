interface MyInterface {
  void method1();
}

<error descr="Class 'SampleEnum1' must implement abstract method 'method1()' in 'MyInterface'">enum SampleEnum1 implements MyInterface</error> {
  ONE;
}

enum SampleEnum2 implements MyInterface {
  <error descr="Enum constant 'ONE' must implement abstract method 'method1()' in 'MyInterface'">ONE</error>{};
}

enum SampleEnum3 implements MyInterface {
  ONE;

  @Override
  public void method1() { }
}

enum SampleEnum4 implements MyInterface {
  ONE {
    @Override
    public void method1() { }
  };
}

enum SampleEnum5 implements MyInterface {
  <error descr="Enum constant 'ONE' must implement abstract method 'method1()' in 'MyInterface'">ONE</error>("one") {};

  SampleEnum5(final String name) {}
}


enum SampleEnum6 implements MyInterface {
  ONE("one") {
    @Override
    public void method1() {}
  };

  SampleEnum6(final String name) {}
}
