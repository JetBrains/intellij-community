import java.util.*;

public class Outer {
  private final int value = calcValue();

  int myValue;
  Set set = new HashSet();

  int calcValue() {
    return set.size();
  }

  void initMyValue() {
    myValue = value;
  }
}