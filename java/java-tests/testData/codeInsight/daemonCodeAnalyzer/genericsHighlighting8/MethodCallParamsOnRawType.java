class TestOnRawType {
  public static void main(String[] args) {
    new FooGenerator().process(TestOnRawType.class);
    new FooGenerator().process(AFoo.class);
    new FooGenerator().process(MFoo.class);
    new FooGenerator<String>().process<error descr="'process(java.lang.Class<TestOnRawType.AFoo>)' in 'TestOnRawType.FooGenerator' cannot be applied to '(java.lang.Class<TestOnRawType>)'">(TestOnRawType.class)</error>;
    new FooGenerator<String>().process(AFoo.class);
    new FooGenerator<String>().process<error descr="'process(java.lang.Class<TestOnRawType.AFoo>)' in 'TestOnRawType.FooGenerator' cannot be applied to '(java.lang.Class<TestOnRawType.MFoo>)'">(MFoo.class)</error>;
  }

  static class AFoo {}
  static class MFoo extends AFoo {}
  static class FooGenerator<T> {
    public void process(Class<AFoo> cls) {
    }
  }
}

class TestNonGenericType {
  public static void main(String[] args) {
    new FooGenerator().process<error descr="'process(java.lang.Class<TestNonGenericType.AFoo>)' in 'TestNonGenericType.FooGenerator' cannot be applied to '(java.lang.Class<TestNonGenericType>)'">(TestNonGenericType.class)</error>;
    new FooGenerator().process(AFoo.class);
    new FooGenerator().process<error descr="'process(java.lang.Class<TestNonGenericType.AFoo>)' in 'TestNonGenericType.FooGenerator' cannot be applied to '(java.lang.Class<TestNonGenericType.MFoo>)'">(MFoo.class)</error>;
  }

  static class AFoo {}
  static class MFoo extends AFoo {}
  static class FooGenerator {
    public void process(Class<AFoo> cls) {
    }
  }
}
