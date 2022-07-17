// "Make annotation applicable to fields" "true"
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.FIELD,})
@interface Foo {}

class Main {
  @Foo int x = 42;
}