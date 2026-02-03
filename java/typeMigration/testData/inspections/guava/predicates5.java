import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

public class Main7 {

  public static void main(String[] args) {
    Optional<? extends String> image = FluentIterable.from(new ArrayList<String>()).firstMatch(getPredicate());
    if (image.isPresent()) {
      System.out.println(image.get());
    }
  }

  static Predicate<caret><String> getPredicate() {
    return null;
  }
}