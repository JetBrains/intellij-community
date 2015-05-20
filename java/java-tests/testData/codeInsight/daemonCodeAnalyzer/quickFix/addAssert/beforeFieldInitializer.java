// "Assert 'myFoo != null'" "false"
class A{
  private final String myFoo = null;
  String myBar = my<caret>Foo.substring(0);
}