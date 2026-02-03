import static BeanUtil.A.NOT_NULL;
class BeanUtil {
  public static class A {
    public static final Object NOT_NULL = null;
  }
  @NN<caret>
  String foo() {
    return null;
  }
}

@interface NotNull {}
