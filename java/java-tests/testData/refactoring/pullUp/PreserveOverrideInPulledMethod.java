public class Test {
  abstract class Base extends IntImpl {
    @Override
    public abstract String<caret> foo();
  }

  class IntImpl extends Int {}
  
  class Int {
    public abstract String foo();
  }
}