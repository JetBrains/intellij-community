// "Replace with qualifier" "true"
import java.util.function.Predicate;

class Test implements Predicate<String> {
  void foo(){
    Predicate<String> stringPredicate = this::te<caret>st;
  }
  
  public boolean test(String s) {
    return true;
  }
}