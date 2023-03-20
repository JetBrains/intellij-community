// "Assert 'myFoo != null'" "true-preview"
class A{
  private final String myFoo = Math.random() > 0.5 ? "" : null;
  String myBar = myFoo.su<caret>bstring(0);
}