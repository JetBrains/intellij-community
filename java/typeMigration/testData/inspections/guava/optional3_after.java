import com.google.common.base.Predicate;

import java.util.ArrayList;
import java.util.Optional;

public class Main7 {

  public static void main(String[] args) {
    Optional<? extends String> image = new ArrayList<String>().stream().filter(getPredicate()::apply).findFirst();
    if (image.isPresent()) {
      System.out.println(image.get());
    }
  }

  static Predicate<String> getPredicate() {
    return null;
  }
}