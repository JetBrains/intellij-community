import java.io.IOException;
import java.util.function.Consumer;

class Action {
  public void acting(String s) throws IOException {
    System.out.println(s);
  }
}


class Client {
  public static void consumer(String val, Consumer<String> func) {
    func.accept(val);
  }

  public static void main(String[] args) {
    Action a = new Action();
    consumer("hello", (s) -> {
        try {
            a.acting(s);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    });
  }
}