import com.google.common.base.Function;

public class MethodReference {
  public void context() {
    Function<String, Integer> fun<caret>ction2 = String::length;

  }
}