// "Replace with '.of()'" "true"
import static java.util.Optional.ofNullable;

class A{
  void test(){
    ofNullable(1<caret>1);
  }
}