import org.jetbrains.annotations.*;

public class FieldLocalNoAliasing {
  static class X {
    int a = 1;
  }

  void noAliasingPossible(X b) {
    X x = getX();
    x.a = 1;
    b.a = 211;
    if (<warning descr="Condition 'x.a == 1' is always 'true'">x.a == 1</warning>) {
      System.out.println("1");
    }
  }

  @Contract(value="->new", pure=true)
  private X getX(){
    X x = new X();
    return x;
  }
}