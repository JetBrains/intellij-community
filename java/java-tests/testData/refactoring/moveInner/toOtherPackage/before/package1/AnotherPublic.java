package package1;

public class AnotherPublic {
   protected void foo(){}
}

class OuterClass {
  private InnerClass instance = new InnerClass();

  private static class InnerClass extends AnotherPublic {
    {
      foo();
    }
  }
}
