// "Replace '@Override' with '{@code @Override}'" "true"

class Foo {

  /**
   * @Override<caret>
   * <pre>
   *   {@code hello, world}
   * </pre>
   */
  public void foo(){

  }
}
