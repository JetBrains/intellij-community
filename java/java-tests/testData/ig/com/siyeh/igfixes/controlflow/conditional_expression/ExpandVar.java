import java.util.*;

class Foo {
  void m(boolean b){
    var p = b <caret>? Arrays.asList(1) : Arrays.asList(2); 
  }
}