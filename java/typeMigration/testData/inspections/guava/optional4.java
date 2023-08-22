import com.google.common.base.Optional;
import com.google.common.base.Predicate;

class Main {
  public static void main(String[] args) {
    Predicate<Object> p1 = integer -> true;
    Optional.of(1).tran<caret>sform(p1::apply);
  }
}