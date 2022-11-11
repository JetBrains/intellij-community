// "Replace with '.of()'" "true-preview"
import static java.util.Optional.*;

class A{
  void test(){
    ofNullable(1<caret>1);
  }
}