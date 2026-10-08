public class RecordPatternWildcardComponent {
  sealed interface MyInterface {}

  record MyRecord<T>(String name, T value) implements MyInterface {}

  static void negated(MyInterface myInterface) {
    if (!(myInterface instanceof MyRecord<?>(String name, Object value))) {
      return;
    }
    if (value != null) {
      System.out.println("Value is not null");
    }
    else {
      System.out.println("Value is null");
    }
  }

  static void positive(MyInterface myInterface) {
    if (myInterface instanceof MyRecord<?>(String name, Object value)) {
      if (value != null) {
        System.out.println("Value is not null");
      }
      else {
        System.out.println("Value is null");
      }
    }
  }
}
