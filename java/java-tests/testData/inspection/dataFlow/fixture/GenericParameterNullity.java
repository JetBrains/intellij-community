import foo.*;

class Test {

  public void test() {

    @NotNull
    String a = notNull(getNullable());

    @NotNull
    String b = notNull(null);
  }

  @Nullable
  public String getNullable() {
    return null;
  }

  @NotNull
  private static <T> T notNull(@Nullable T <warning descr="Method fails when parameter 'value' is 'null'">value</warning>) {

    if (value == null) {
      throw new RuntimeException("null");
    }

    return value;
  }
}