import java.lang.annotation.*;

@Target({ElementType.TYPE_USE, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@interface FooAnno {}

public class TypeUseAndMemberAnnotations {
  @FooAnno
  public Object foo1() {
    return null;
  }

  public Object foo2(@FooAnno String arg) {
    return null;
  }

  public @FooAnno java.util.List<@FooAnno String> returnType() { return null; }

}