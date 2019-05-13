// "Replace with qualifier" "true"
import java.util.function.Predicate;

class Test {
  void foo(Predicate<String> p){
    Predicate<String> stringPredicate = p::te<caret>st;
  }
}