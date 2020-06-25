import org.jetbrains.annotations.Nullable;

import java.util.*;

// IDEA-244356
class Usage {

  private static void exampleMethod(List<? extends Integer> listOfIntegers) {
    int firstElement = listOfIntegers.get(0);
    for (int element : listOfIntegers) {
      if (element == firstElement) {}
    }
  }

  public static void main(String[] args) {
    List<Integer> list = new ArrayList<>();
    list.add(0);
    list.add(1);
    exampleMethod(list);
  }
}