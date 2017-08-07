// "Replace with qualifier" "false"
import java.util.function.Predicate;

class Test {
  void foo(Predicate<String> p){
    Predicate<String> stringPredicate = t -> p.te<caret>st("");
  }
}