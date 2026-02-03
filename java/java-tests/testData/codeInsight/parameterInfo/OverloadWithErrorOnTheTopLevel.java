
import java.util.Collections;
import java.util.List;

public class Bug {
  private static <TFrom, TTo> List<TTo> mapAsList(TFrom source, Class<TTo> toType) {
    return Collections.singletonList((TTo) source);
  }

  private static <TFrom, TTo> List<TTo> mapAsList(TFrom source, Class<TTo> toType, boolean someOption) {
    return Collections.singletonList((TTo) source);
  }

  private static class Foo {
    public Foo(Long value, List<Long> values, List<Long> moreValues) {
    }
  }

  public static void main(String[] args) {
    new Foo(
      1L,
      mapAsList(2L, Long.class),
      mapAsList(3L, Lo<caret>ng.class, true)
    );
  }
}
