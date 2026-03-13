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
    @Override
    public String test1(String... param) {
        return "";
    }

    @Override
    public String test2(String... param) {
        return "";
    }

    @Override
    public String test3(String @Nullable ... param) {
        return "";
    }

    @Override
    public String test4(String... param) {
        return "";
    }

    @Override
    public String test5(String... param) {
        return "";
    }

    @Override
    public String test6(String @Nullable ... param) {
        return "";
    }

    @Override
    public String test7(@Nullable String... param) {
        return "";
    }

    @Override
    public String test8(@Nullable String... param) {
        return "";
    }

    @Override
    public String test9(@Nullable String @Nullable ... param) {
        return "";
    }
}
