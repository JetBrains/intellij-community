import java.lang.annotation.*;

@Target({ElementType.TYPE_USE})
@interface TA { }

class Test {
  private void bar(java.util.@TA List<String> na<caret>me) {
  }


}