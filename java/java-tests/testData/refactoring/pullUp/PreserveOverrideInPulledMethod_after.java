public class Test {
  abstract class Base extends IntImpl {
  }

  abstract class IntImpl extends Int {
      @Override
      public abstract String foo();
  }
  
  class Int {
    public abstract String foo();
  }
}