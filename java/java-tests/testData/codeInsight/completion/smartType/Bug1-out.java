
public class QQQ {
  void f(QQQ q) { }

  {
    new Runnable() {
      public void run() {
        f(QQQ.this);<caret>
      }
    };
  }
}
