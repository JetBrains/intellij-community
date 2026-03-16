import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

interface Super {
  String test1(String... param);

  String test2(String @NonNull ... param);

  String test3(String @Nullable ... param);

  String test4(@NonNull String... param);

  String test5(@NonNull String @NonNull ... param);

  String test6(@NonNull String @Nullable ... param);

  String test7(@Nullable String... param);

  String test8(@Nullable String @NonNull ... param);

  String test9(@Nullable String @Nullable ... param);
}

@NullMarked
public class SubClass implements Super {
    <caret>
}
