// "Assert 'myFoo != null'" "true"
class A{
  private final String myFoo = null;
  String myBar;

    {
        assert myFoo != null;
        myBar = myFoo.substring(0);
    }
}