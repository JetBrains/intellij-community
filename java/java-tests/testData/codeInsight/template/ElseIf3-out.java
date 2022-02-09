import java.util.*;

class Foo {
  void test(int x){
    if(x > 0) {}
    else if(x == 0) {} else if () {
        <caret>
    } else {}
  }
}