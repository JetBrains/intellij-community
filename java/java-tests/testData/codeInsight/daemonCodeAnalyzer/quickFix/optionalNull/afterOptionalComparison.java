// "Fix all 'Null value for Optional type' problems in file" "true"
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public class Test {
  void test(Optional<String> opt) {
    if(!opt.isPresent()) {
      System.out.println("null!");
    }
  }

  void test2 (Optional<String> opt) {
      /*comment*/
      if(opt.isPresent()) {
      System.out.println("null!");
    }
  }

}