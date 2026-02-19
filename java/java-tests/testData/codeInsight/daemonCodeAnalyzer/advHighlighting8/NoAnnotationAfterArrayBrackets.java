import java.lang.annotation.*;

class X {
  private @Nullable String <error descr="Annotations are not allowed here">@Nullable</error> justField;
  private @Nullable String @Nullable [] <error descr="Annotations are not allowed here">@Nullable</error> arrayField;
  
  void test(@Nullable String @Nullable [] <error descr="Annotations are not allowed here">@Nullable</error> arrayParam,
            @Nullable String <error descr="Annotations are not allowed here">@Nullable</error> justParam,
            @Nullable String @Nullable [] @Nullable ... ellipsisParam) {
    @Nullable String @Nullable [] <error descr="Annotations are not allowed here">@Nullable</error> arrayVar;
    @Nullable String <error descr="Annotations are not allowed here">@Nullable</error> justVar;
  }
}
@Target(ElementType.TYPE_USE)
@interface Nullable {}