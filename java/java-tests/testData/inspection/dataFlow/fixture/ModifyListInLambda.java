import java.util.List;
import org.jetbrains.annotations.*;

public class ModifyListInLambda {
  @Contract(pure = true)
  Runnable getRunnable(List<String> list) {
    return () -> list.add("foo");
  }
}