public class CaretAtBody {
  void s() {
    new Thread(() -> <caret>System.out.println("started")).start();
  }
}