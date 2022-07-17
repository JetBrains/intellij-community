// "Assert 'myFoo != null'" "true"
class A{
  private final String myFoo = Math.random() > 0.5 ? "" : null;
  String myBar;

    {
        assert myFoo != null;
        myBar = myFoo.substring(0);
    }
}