import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class MyClass {
  @Target(value = {ElementType.FI<caret>ELD})
  public @interface MyAnnotations {}
}
