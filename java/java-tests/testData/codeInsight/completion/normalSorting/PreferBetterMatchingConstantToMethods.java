class Foo {
  String s = Util.se<caret>
}

class Util {
  public static final String serial = "serialVersionUID";

  public static Runnable superExpressionInIllegalContext() {}

}