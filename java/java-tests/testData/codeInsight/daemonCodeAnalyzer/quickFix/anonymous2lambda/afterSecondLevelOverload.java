// "Replace with lambda" "true"

import java.util.function.Function;

abstract class LambdaConvert {

  public abstract <T> T query(Function<Double, T> rse);

  public void with() {
    add(query((Function<Double, String>) resultSet -> ""));
  }

  public void add(String s) {}
  public void add(Integer i) {}
}