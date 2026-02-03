public class LambdaInitialization {
  Runnable foo() {
    Runnable <caret>s = () -> {
      s();
    };
    return s;
  }
}