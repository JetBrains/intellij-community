import org.jetbrains.annotations.NotNull;

public class CustomExceptionType {
  public void foo(Object obj, @NotNull(exception = NullPointerException.class) Object obj2) { }
}