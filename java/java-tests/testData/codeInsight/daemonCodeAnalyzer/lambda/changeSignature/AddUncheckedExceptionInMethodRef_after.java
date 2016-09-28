import java.util.function.Consumer;

class Action {
  public void acting(String s) throws NullPointerException {
    System.out.println(s);
  }
}


class Client {
  public static void consumer(String val, Consumer<String> func) {
    func.accept(val);
  }

  public static void main(String[] args) {
    Action a = new Action();
    consumer("hello", a::acting);
  }
}