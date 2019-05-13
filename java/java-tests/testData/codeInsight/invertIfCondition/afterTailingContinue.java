// "Invert 'if' condition" "true"
public class C {
    public void test() {
    for (int i = 0; i < 42; i++) {
        if (i != 7) {
            continue;
        }
        else {
            //c1
            return;
        }
    }
  }
}
