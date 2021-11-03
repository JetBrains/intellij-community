import java.util.function.Predicate;
import java.util.*;

public class PredicateNot {
  void test(List<String> list) {
    list.stream()
      .filter(Predicate.not(String::isEmpty))
      .filter(Predicate.not(<warning descr="Method reference result is always 'false'">String::isEmpty</warning>))
      .forEach(s -> {
        if (<warning descr="Condition 's.length() == 0' is always 'false'">s.length() == 0</warning>) {
          System.out.println("Empty!");
        }
      });
  }
}