
import java.util.function.Function;

abstract class LambdaConvert {

  public abstract <T> T query(Function<Double, T> rse);

  public void with() {
    addSingle(query((<warning descr="Casting 'resultSet -> {...}' to 'Function<Double, String>' is redundant">Function<Double, String></warning>)resultSet -> ""));
    add(query((Function<Double, String>)resultSet -> ""));
  }

  public void addSingle(String s) {}

  public void add(String s) {}
  public void add(Integer i) {}
}