// "Replace with qualifier" "true"
import java.util.function.Predicate;

class Test implements Predicate<String> {
  void foo(){
    Predicate<String> stringPredicate = this;
  }
  
  public boolean test(String s) {
    return true;
  }
}