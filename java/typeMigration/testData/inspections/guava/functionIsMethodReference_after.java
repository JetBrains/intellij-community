import java.util.function.Function;

public class MethodReference {
  public void context() {
    Function<String, Integer> function2 = String::length;

  }
}