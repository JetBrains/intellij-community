// "Create Class 'ClassB'" "true"
public class ClassA
{
  public void mA(Object arg)
  {
    ((ClassB) arg).foo();
  }
}

public class <caret>ClassB {
}