// "Remove 'getClass()' call" "true"
public class Main {
  Class<?> test(Class<?> obj) {
    return switch(obj.hashCode()) {
      default -> obj.<caret>getClass();
    };
  }
}