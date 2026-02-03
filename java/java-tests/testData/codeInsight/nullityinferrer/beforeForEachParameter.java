import java.util.*;
class Test {
   void foo(List requests){
        for (Object request : requests) {
          System.out.println(request.toString());
        }
    }
}