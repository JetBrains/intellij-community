import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class TypeUseWithType {

  @TestAnnotation1
  public String x() {
    return null;
  }

  @Target(value = { ElementType.TYPE_USE, ElementType.METHOD })
  public @interface TestAnnotation1 {
  }
}