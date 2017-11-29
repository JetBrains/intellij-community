// "Replace with collect" "true"
import java.util.*;

class A {
  public static void main(List<String> args) {
    ArrayList<String> uniqNames = new ArrayList<>();
    for (String name : ar<caret>gs){
      uniqNames.add(name.substring(1));
    }
    uniqNames.forEach(System.out::println);
  }


}