import org.jetbrains.annotations.*;
import java.util.*;

public class ReturnOrElseNull {
  static @Nullable String getStr(List<String> list) {
    return list.stream().filter(s -> !s.isEmpty()).findFirst().orElse(null);
  }
}