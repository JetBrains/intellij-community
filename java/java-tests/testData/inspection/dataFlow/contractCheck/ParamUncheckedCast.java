import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

class Foo {
  // IDEA-229184
  @Contract(value = "_ -> param1", pure = true)
  @SuppressWarnings("unchecked")
  public static <T> Option<T> narrow(@NotNull Option<? extends T> option) {
    return ((Option<T>) option);
  }
  
  interface Option<T> {}
}
