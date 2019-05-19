import java.util.*;

public class Outer {
  private final int value = calcValue();

  int myValue;
  Set set = new <error descr="Cannot resolve symbol 'HashSet'">HashSet</error>();

  int calcValue() {
    return set.size();
  }

  void initMyValue() {
    myValue = value;
  }
}