import org.jetbrains.annotations.*;

public class DereferenceInThrowMessage {
  void test(@Nullable Object obj) {
    if (obj != null && obj.hashCode() == 0) {
      throw new RuntimeException("Hashcode: " + obj.hashCode());
    }
  }
}