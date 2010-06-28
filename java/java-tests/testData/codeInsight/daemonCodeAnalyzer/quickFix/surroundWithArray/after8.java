// "Surround with array initialization" "true"
import java.util.*;
class A {

  void foo(){
    Arrays.sort(new String[]{"Str"}, new Comparator<String>(){
            public int compare(String o1, String o2) {
                return 0;
            }
        });
  }
}