public class ArrayInitBeforeSuper {
  public static void main(String[] args) {
  }

  public class Example extends Zuper{
    public class Inner{
    }
    Example() {
      super(new Inner[]{}); // <<<< NOT ERROR
    }
  }

  class Zuper {
    Zuper(Example.Inner[] inners) {
    }
  }
}