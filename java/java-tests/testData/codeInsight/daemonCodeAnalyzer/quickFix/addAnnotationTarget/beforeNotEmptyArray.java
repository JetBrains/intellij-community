// "Add the 'FIELD' target" "true"
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@interface Foo {}

class Main {
  @Foo<caret> int x = 42;
}