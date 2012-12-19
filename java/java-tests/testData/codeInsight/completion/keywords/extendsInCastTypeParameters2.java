public class Main {

  public int compare(Object value1, Object value2) {
    if (value1 instanceof Comparable && value2 instanceof Comparable) {
      return ((Comparable<? <caret>>) value1).compareTo(value2);
    }
    throw new IllegalArgumentException();
  }
}
