import java.lang.Runnable;
import java.util.List;
import java.util.ArrayList;

public class A {
  void foo(List<String> parameter) {
    new Runnable(parameter<caret>) {}
  }
}
