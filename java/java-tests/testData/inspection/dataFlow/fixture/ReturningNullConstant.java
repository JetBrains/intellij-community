import org.jetbrains.annotations.NotNull;

class BrokenAlignment {

  private static final String DEFAULT = null;

  @NotNull
  public String getDefaultValue() {
    return <warning descr="'null' is returned by the method declared as @NotNull">DEFAULT</warning>;
  }
}