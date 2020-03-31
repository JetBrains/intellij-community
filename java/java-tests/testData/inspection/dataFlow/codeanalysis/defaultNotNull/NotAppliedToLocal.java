import codeanalysis.experimental.annotations.DefaultNotNull;
import codeanalysis.experimental.annotations.Nullable;

@DefaultNotNull
class NullnessDemo {
  @Nullable Object something() {
    return null;
  }

  void foo() {
    Object o = something();
  }
}
