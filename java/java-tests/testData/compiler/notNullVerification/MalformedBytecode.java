import java.lang.annotation.*;
import java.util.*;
import org.jetbrains.annotations.NotNull;

public abstract class MalformedBytecode {

  public static void main() {
    List<String> list = new ArrayList<>();
    list.add("aaa");
    list.add(null);
    list.add("bbb");
    new NullTest2().processList(list);
  }

  public static abstract class NullTest1<T> {
    protected abstract void processList(T list);
  }

  public static class NullTest2<T, C extends Collection<@Nullable T>> extends NullTest1<C> {
    public void processList(C list) {
      for (@Nullable T s1 : list) {
        handle(s1);
      }
    }

    void handle(@NotNull T arg) {
    }

  }
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE_USE })
@interface Nullable { }