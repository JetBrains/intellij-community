import java.lang.annotation.*;

@Target({ElementType.TYPE_USE, ElementType.METHOD, ElementType.PARAMETER})
@interface FooAnno {}

public class TypeUseAndMemberAnnotations {
  @FooAnno
  public Object foo1() {
    return null;
  }

  public Object foo2(@FooAnno String arg) {
    return null;
  }

}