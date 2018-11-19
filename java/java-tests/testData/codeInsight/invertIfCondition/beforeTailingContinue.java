// "Invert 'if' condition" "true"
public class C {
    public void test() {
    for (int i = 0; i < 42; i++) {
      i<caret>f (i == 7) {
          //c1
        return;
      }
      continue;
    }
  }
}
