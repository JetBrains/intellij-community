import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;


interface Super {
  List<String> test1(List<String> param);

  List<@NonNull String> test2(List<@NonNull String> param);

  List<@Nullable String> test3(List<@Nullable String> param);

  @NonNull List<String> test4(@NonNull List<String> param);

  @NonNull List<@NonNull String> test5(@NonNull List<@NonNull String> param);

  @NonNull List<@Nullable String> test6(@NonNull List<@Nullable String> param);

  @Nullable List<String> test7(@Nullable List<String> param);

  @Nullable List<@NonNull String> test8(@Nullable List<@NonNull String> param);

  @Nullable List<@Nullable String> test9(@Nullable List<@Nullable String> param);
}

@NullMarked
public class SubClass implements Super {
    @Override
    public List<String> test1(List<String> param) {
        return Collections.emptyList();
    }

    @Override
    public List<String> test2(List<String> param) {
        return Collections.emptyList();
    }

    @Override
    public List<@Nullable String> test3(List<@Nullable String> param) {
        return Collections.emptyList();
    }

    @Override
    public List<String> test4(List<String> param) {
        return Collections.emptyList();
    }

    @Override
    public List<String> test5(List<String> param) {
        return Collections.emptyList();
    }

    @Override
    public List<@Nullable String> test6(List<@Nullable String> param) {
        return Collections.emptyList();
    }

    @Override
    public @Nullable List<String> test7(@Nullable List<String> param) {
        return Collections.emptyList();
    }

    @Override
    public @Nullable List<String> test8(@Nullable List<String> param) {
        return Collections.emptyList();
    }

    @Override
    public @Nullable List<@Nullable String> test9(@Nullable List<@Nullable String> param) {
        return Collections.emptyList();
    }
}
