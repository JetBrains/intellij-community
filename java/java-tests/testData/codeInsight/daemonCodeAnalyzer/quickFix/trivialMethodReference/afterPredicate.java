// "Replace with qualifier" "true-preview"
import java.util.function.Predicate;

class Test {
  void foo(Predicate<String> p){
    Predicate<String> stringPredicate = p;
  }
}