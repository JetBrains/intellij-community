import typeUse.*;

class Bar<T extends @NotNull Object> {
  public static void main(String[] args) {
    Bar<@NotNull S<caret>tring> bar;
  }
}