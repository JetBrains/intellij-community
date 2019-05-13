// "Replace method call on lambda with lambda body" "false"
import java.util.function.Supplier;

class Test {
  {
    System.out.println(((Supplier<String>)() -> {
      switch () {
        default:
          return "";
      }
    }).g<caret>et());
  }
}