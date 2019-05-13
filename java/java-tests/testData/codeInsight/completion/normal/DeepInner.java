class ClassMain{
  public static class ClassInner1{
    public static class ClassInner2{

    }
  }
}

class Foo {
  {
    new ClassMain.Cla<caret>
  }
}