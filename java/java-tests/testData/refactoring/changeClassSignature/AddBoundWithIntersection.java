import java.io.Serializable;

class <caret>C {}

class Some implements Runnable, Serializable {
    void m() {
      C c = new C();
    }
}