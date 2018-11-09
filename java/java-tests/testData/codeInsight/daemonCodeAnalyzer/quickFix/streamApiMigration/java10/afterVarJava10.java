// "Fix all 'Loop can be collapsed with Stream API' problems in file" "true"
import java.util.*;
import java.util.stream.Collectors;

class Test {
  public List<Integer> someMethod() {
      ArrayList<Integer> listOfInteger;
    Integer[] arrayOfInteger = {2, 4, 8};
      listOfInteger = Arrays.stream(arrayOfInteger).filter(number -> number >= 4).collect(Collectors.toCollection(ArrayList::new));
    return listOfInteger;
  }
}