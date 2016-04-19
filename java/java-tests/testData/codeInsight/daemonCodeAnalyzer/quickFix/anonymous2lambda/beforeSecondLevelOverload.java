// "Replace with lambda" "false"

import java.util.function.Function;

abstract class LambdaConvert {

  public abstract <T> T query(Function<Double, T> rse);

  public void with() {
    add(query(new Function<Do<caret>uble, String>() {
      @Override
      public String apply(Double resultSet) {
        return "";
      }
    }));
  }

  public void add(String s) {}
  public void add(Integer i) {}
}