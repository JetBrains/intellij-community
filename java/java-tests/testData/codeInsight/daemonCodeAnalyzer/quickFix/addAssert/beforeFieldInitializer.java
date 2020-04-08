// "Assert 'myFoo != null'" "true"
class A{
  private final String myFoo = null;
  String myBar = myFoo.su<caret>bstring(0);
}