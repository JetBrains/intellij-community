import javax.annotation.Nonnull;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class Test {
  static class Super {
    @Nonnull
    public String foo(@Nonnull String foo) {
      return "";
    }
  }

  @NonNullApi
  static class FooSub extends Super {

    @Override
    public String foo(String foo) {
      return super.foo(foo);
    }
  }

  @Target(ElementType.TYPE)
  @javax.annotation.Nonnull
  @javax.annotation.meta.TypeQualifierDefault({ElementType.METHOD, ElementType.PARAMETER})
  public @interface NonNullApi {
  }
}