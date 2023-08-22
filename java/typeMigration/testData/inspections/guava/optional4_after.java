import com.google.common.base.Predicate;

import java.util.Optional;

class Main {
  public static void main(String[] args) {
    Predicate<Object> p1 = integer -> true;
    Optional.of(1).map<caret>(p1::apply);
  }
}