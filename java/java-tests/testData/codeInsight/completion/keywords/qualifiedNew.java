public class Test {
  public Inner getInner() {
    return this.<caret>
  }

  public class Inner {
    public void foo() {
    }
  }
}