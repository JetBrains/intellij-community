// "Unwrap 'switch'" "true-preview"
public class One {
  void f1(String a) {
    sw<caret>itch (a) {
      case default -> System.out.println("None");
    }
  }
}