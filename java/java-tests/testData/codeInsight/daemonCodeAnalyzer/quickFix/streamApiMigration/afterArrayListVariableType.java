// "Replace with collect" "true"
import java.util.*;
import java.util.stream.Collectors;

class A {
  public static void main(List<String> args) {
      ArrayList<String> uniqNames = args.stream().map(name -> name.substring(1)).collect(Collectors.toCollection(ArrayList::new));
      uniqNames.forEach(System.out::println);
  }


}