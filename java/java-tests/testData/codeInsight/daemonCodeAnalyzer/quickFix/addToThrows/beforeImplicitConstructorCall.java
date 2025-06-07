// "Add exception to method signature" "true-preview"
import java.io.IOException;

class Super {
  Super() throws IOException {
    
  }
}
class Sub extends Super {
  Sub<caret>(int x) {
    
  }
}