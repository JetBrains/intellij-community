// "Replace with enhanced 'switch' statement" "true-preview"
import java.util.*;

class CommentsInside {

  void test(int x) {
      switch (x) {
          case 0 -> {
              // nothing to do
          }
          case 1 -> System.out.println(x);
      }
  }
}