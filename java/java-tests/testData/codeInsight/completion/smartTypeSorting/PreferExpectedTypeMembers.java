class MyColor {
  static final MyColor RED = null;
}
class Another {
  static final MyColor RED = null;
}

class Foo {
  public static final MyColor MARKED_BACKGROUND = new MyColor();
  
  {
    MyColor color = RED<caret>
  }
}