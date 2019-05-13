import org.jetbrains.annotations.NotNull;

interface MakeNonNull {
  @NotNull
  String getSnapshot(@NotN<caret>ull Integer arg);
}

class MakeNonNullImpl implements MakeNonNull {
  @NotNull
  @Override
  public String getSnapshot(@NotNull Integer arg) {
    return "1";
  }
}

class MakeNonNullImpl2 implements MakeNonNull {
  @NotNull
  @Override
  public String getSnapshot(@NotNull Integer arg) {
    return "1";
  }
}