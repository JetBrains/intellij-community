import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class TypeUseWithType {

  <warning descr="Missorted modifiers '@TestAnnotation1 private'">@TestAnnotation1<caret></warning> private String fieldOneLine;

  @Target(value = { ElementType.TYPE_USE, ElementType.FIELD })
  public @interface TestAnnotation1 {
  }
}