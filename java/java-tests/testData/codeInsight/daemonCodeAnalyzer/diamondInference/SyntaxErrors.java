import java.util.*;
import java.util.function.*;

class SyntaxErrors {
  void test() {
    Object array = new ArrayList<error descr="Array creation with '<>' not allowed"><></error>[10];
    Supplier<List<String>> ref = ArrayList<<error descr="Identifier expected">></error>::new;
    List<<error descr="Identifier expected">></error> local = null;
    Object cast = (List<<error descr="Identifier expected">></error>) null;
  }
}
