import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

interface Super {
  String[] test1(String[] param);

  String @NonNull [] test2(String @NonNull [] param);

  String @Nullable [] test3(String @Nullable [] param);

  @NonNull String[] test4(@NonNull String[] param);

  @NonNull String @NonNull [] test5(@NonNull String @NonNull [] param);

  @NonNull String @Nullable [] test6(@NonNull String @Nullable [] param);

  @Nullable String[] test7(@Nullable String[] param);

  @Nullable String @NonNull [] test8(@Nullable String @NonNull [] param);

  @Nullable String @Nullable [] test9(@Nullable String @Nullable [] param);
}

@NullMarked
public class SubClass implements Super {
    @Override
    public String[] test1(String[] param) {
        return new String[0];
    }

    @Override
    public String[] test2(String[] param) {
        return new String[0];
    }

    @Override
    public String @Nullable [] test3(String @Nullable [] param) {
        return new String[0];
    }

    @Override
    public String[] test4(String[] param) {
        return new String[0];
    }

    @Override
    public String[] test5(String[] param) {
        return new String[0];
    }

    @Override
    public String @Nullable [] test6(String @Nullable [] param) {
        return new String[0];
    }

    @Override
    public @Nullable String[] test7(@Nullable String[] param) {
        return new String[0];
    }

    @Override
    public @Nullable String[] test8(@Nullable String[] param) {
        return new String[0];
    }

    @Override
    public @Nullable String @Nullable [] test9(@Nullable String @Nullable [] param) {
        return new String[0];
    }
}
