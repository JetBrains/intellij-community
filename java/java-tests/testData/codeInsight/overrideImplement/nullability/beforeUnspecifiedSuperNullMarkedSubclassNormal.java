import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

interface Super {
  String test1(String param);

  @NonNull String test2(@NonNull String param);

  @Nullable String test3(@Nullable String param);
}

@NullMarked
public class SubClass implements Super {
    <caret>
}
