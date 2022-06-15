import java.util.concurrent.Callable;

public class AtTypeCast {
  void test(Object object) {
    Object obj = null;
    Object res = ((<caret>Callable<>)obj).call();
  }
}