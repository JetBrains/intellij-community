// "Create class 'ClassB'" "true"
public class ClassA
{
  public void mA(Object arg)
  {
    ((<caret>ClassB) arg).foo();
  }
}
