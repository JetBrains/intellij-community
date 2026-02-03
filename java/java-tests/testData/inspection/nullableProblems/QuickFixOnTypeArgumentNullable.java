import typeUse.*;

class Bar<T extends @NotNull Object> {
  public static void main(String[] args) {
    Bar<<warning descr="Non-null type argument is expected">@Nullable S<caret>tring</warning>> bar;
  }
}