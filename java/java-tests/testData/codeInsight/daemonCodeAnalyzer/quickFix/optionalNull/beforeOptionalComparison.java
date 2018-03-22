// "Fix all 'Null value for Optional type' problems in file" "true"
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public class Test {
  void test(Optional<String> opt) {
    if(opt =<caret>= null) {
      System.out.println("null!");
    }
  }

  void test2 (Optional<String> opt) {
    if(opt !=/*comment*/ null) {
      System.out.println("null!");
    }
  }

  void test3 (Optional<String> opt) {
    // this case is ignored
    if(opt == null || !opt.isPresent()) {
      System.out.println("null or absent");
    }
  }

  void test4 (Optional<String> opt) {
    // this case is ignored as well
    if(opt != null && opt.isPresent()) {
      System.out.println("present");
    }
  }

  void test5 (Optional<String> opt, Optional<String> opt2) {
    // warn: different optionals used
    if(opt != null && opt2.isPresent()) {
      System.out.println("present");
    }
  }

}