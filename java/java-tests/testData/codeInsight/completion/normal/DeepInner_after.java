class ClassMain{
  public static class ClassInner1{
    public static class ClassInner2{

    }
  }
}

class Foo {
  {
    new ClassMain.ClassInner1.ClassInner2()<caret>
  }
}