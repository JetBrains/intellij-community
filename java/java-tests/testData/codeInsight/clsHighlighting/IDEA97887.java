public class MyChild extends Child<String> {
  @Override
  public void evaluate(InnerImpl impl) {
    String s = impl.t;
  }
}