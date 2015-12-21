import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

public class Main7 {

  public static void main(String[] args) {
    Optional<? extends String> im<caret>age = FluentIterable.from(new ArrayList<String>()).firstMatch(getPredicate());
    if (image.isPresent()) {
      System.out.println(image.get());
    }
  }

  static Predicate<String> getPredicate() {
    return null;
  }
}