// "Replace lambda with method reference" "true-preview"
import java.util.function.Function;

public abstract class x {

  public enum E {
    A(n -> <caret>n.toString()),
    B(n -> { return n.toString();}),
    C(Number::toString);

    E(Function<Number, Object> iFunc) {}
  }

  public void dothis(Function<Number, Object> iFunc) { }

  public void trythis() {
    dothis(n -> n.toString());
  }

  public void trythis2() {
    dothis(n -> { return n.toString(); });
  }
}