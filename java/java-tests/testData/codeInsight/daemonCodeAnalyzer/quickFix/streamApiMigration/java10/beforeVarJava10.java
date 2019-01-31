// "Fix all 'Loop can be collapsed with Stream API' problems in file" "true"
import java.util.*;

class Test {
  public List<Integer> someMethod() {
    var listOfInteger = new ArrayList<Integer>();
    Integer[] arrayOfInteger = {2, 4, 8};
    f<caret>or (var number: arrayOfInteger) {
      if (number >= 4) {
        listOfInteger.add(number);
      }
    }
    return listOfInteger;
  }
}