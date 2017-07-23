import java.lang.annotation.*;
import java.util.*;

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
  
  public List<@FooAnno String> foo3(List<@FooAnno String> arg) {
    return null;
  }

}
