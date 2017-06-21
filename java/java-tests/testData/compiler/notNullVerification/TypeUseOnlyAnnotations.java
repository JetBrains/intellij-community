import java.lang.annotation.*;

@Target(ElementType.TYPE_USE)
@interface FooAnno {}

public class TypeUseOnlyAnnotations {
  @FooAnno
  public Object foo1() {
    return null;
  }

  public Object foo2(@FooAnno String arg) {
    return null;
  }

}