import org.jetbrains.annotations.*;

public class InferenceNullityMismatch {
  static String getData(Super obj) {
    if (!(obj instanceof Sub)) {
      throw new IllegalArgumentException();
    }
    return obj.calculate();
  } 
}
class Super {
  native @NotNull String calculate();
}
class Sub extends Super {
  native @Nullable String calculate();
}