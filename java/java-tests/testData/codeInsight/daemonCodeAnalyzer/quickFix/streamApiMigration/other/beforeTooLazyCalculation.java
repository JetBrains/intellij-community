// "Replace with forEach" "INFORMATION"
import java.util.*;

class A {
  public static void main(String[] args) {
    List<String> names = Arrays.asList("Bob", "Alice", "Bob", "Carol");
    Set<String> uniqNames = new HashSet<>(names.size());
    for (String name : na<caret>mes){
      uniqNames.add(makeNameUnique(name, uniqNames));
    }
    uniqNames.forEach(System.out::println);
  }

  private static String makeNameUnique(final String name, final Set<String> uniqNames) {
    if (uniqNames.contains(name)) {
      return name + "1";
    }
    return name;
  }


}