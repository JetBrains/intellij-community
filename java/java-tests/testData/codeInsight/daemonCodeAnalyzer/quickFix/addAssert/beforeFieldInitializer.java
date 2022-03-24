// "Assert 'myFoo != null'" "true"
class A{
  private final String myFoo = Math.random() > 0.5 ? "" : null;
  String myBar = myFoo.su<caret>bstring(0);
}