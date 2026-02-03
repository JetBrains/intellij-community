import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class TypeUseWithType {

  private @TestAnn<caret>otation1 String fieldOneLine;

  @Target(value = { ElementType.TYPE_USE, ElementType.FIELD })
  public @interface TestAnnotation1 {
  }
}