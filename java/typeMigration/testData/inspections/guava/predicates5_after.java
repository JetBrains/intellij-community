import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;

import java.util.ArrayList;
import java.util.function.Predicate;

public class Main7 {

  public static void main(String[] args) {
    Optional<? extends String> image = FluentIterable.from(new ArrayList<String>()).firstMatch(getPredicate()::test);
    if (image.isPresent()) {
      System.out.println(image.get());
    }
  }

  static Predicate<String> getPredicate() {
    return null;
  }
}