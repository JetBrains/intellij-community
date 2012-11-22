// "Implement method 'foo'" "false"
abstract class Test {
  public abstract void f<caret>oo();
}

class TImple extends Test {
  private void foo(){}
}