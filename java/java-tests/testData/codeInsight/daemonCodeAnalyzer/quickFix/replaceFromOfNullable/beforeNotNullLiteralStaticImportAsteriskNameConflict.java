// "Replace with '.of()'" "true-preview"
import static java.util.Optional.*;

class A{
  void of() {}

  void test(){
    ofNullable(1<caret>1);
  }
}