import org.jetbrains.annotations.Nullable;
import java.util.function.IntSupplier;

public class LambdaAfterNullCheck {
  @Nullable IntSupplier test(@Nullable String s, @Nullable String s1) {
    if(s == null || s1 == null) return null;
    return () -> fn(s, s1);
  }

  @Nullable String s;
  @Nullable String s1;
  
  @Nullable IntSupplier test2() {
    if(s == null || s1 == null) return null;
    return () -> fn(s, s1);
  }
  
  static int fn(String s, String s1) {
    return s.hashCode()+s1.hashCode();
  }
}
