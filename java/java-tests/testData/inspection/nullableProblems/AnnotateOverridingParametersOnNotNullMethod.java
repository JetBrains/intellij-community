import org.jetbrains.annotations.NotNull;

interface MakeNonNull {
  @NotNull
  String getSnapshot(<warning descr="Overriding method parameters are not annotated">@NotN<caret>ull</warning> Integer arg);
}

class MakeNonNullImpl implements MakeNonNull {
  @NotNull
  @Override
  public String getSnapshot(Integer arg) {
    return arg.toString();
  }
}

class MakeNonNullImpl2 implements MakeNonNull {
  @NotNull
  @Override
  public String getSnapshot(Integer <warning descr="Not annotated parameter overrides @NotNull parameter">arg</warning>) {
    return "1";
  }
}