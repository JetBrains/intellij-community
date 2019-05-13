import java.lang.annotation.*;
import java.util.List;

@Target({ElementType.TYPE_USE})
@interface TA { }

class Test {
  private void bar(@TA List<String> name) {
  }


}