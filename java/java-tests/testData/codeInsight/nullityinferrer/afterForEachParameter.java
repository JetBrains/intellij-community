import org.jetbrains.annotations.NotNull;

import java.util.*;
class Test {
   void foo(@NotNull List requests){
        for (@NotNull Object request : requests) {
          System.out.println(request.toString());
        }
    }
}