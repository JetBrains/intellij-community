package ppp;

public class A {
  public interface Inner {
    void foo();
  }

  private final Inner myInner;

  protected A(Inner inner) {
    myInner = inner;
  }

  protected final Inner getInner() {
    return myInner;
  }
}