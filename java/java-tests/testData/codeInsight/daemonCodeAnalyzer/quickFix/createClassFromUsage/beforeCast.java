// "Create class 'ClassB'" "true-preview"
public class ClassA
{
  public void mA(Object arg)
  {
    ((<caret>ClassB) arg).foo();
  }
}
