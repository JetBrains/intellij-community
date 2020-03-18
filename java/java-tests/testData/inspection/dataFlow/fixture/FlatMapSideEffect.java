import java.util.*;
import java.util.stream.*;

// IDEA-200094
class Main {
  void flatMapAlwaysNull(List<String> input) {
    List<String> side = new ArrayList<>();
    long count = <warning descr="Result of 'input.stream().<String>flatMap(e -> { if(!e.isEmpty()) side.add(e); return null; })....' is always '0'">input.stream().<String>flatMap(e -> {
      if(!e.isEmpty()) side.add(e);
      return null;
    }).count()</warning>;
    if (side.isEmpty()) {} // not known
    if (<warning descr="Condition 'count > 0' is always 'false'">count > 0</warning>) {}
  }

  public static void main(String[] args) {
    new Main().myMethod();
  }

  private void myMethod() {
    List<Integer> numberOne = Collections.singletonList(1);
    List<Integer> collectionBeingModifiedSometimes = new ArrayList<>();

    List<Integer> numberTwo = numberOne.stream().flatMap(entry -> {
      if (Math.random() > 0.5) {
        collectionBeingModifiedSometimes.add(999);
      }
      return Stream.of(entry * 2);
    }).collect(Collectors.toList());

    String text = collectionBeingModifiedSometimes.size() == 0 ? "empty" : "nonempty";
    System.out.println("" + numberOne + " " + numberTwo + " " + text);
  }
}