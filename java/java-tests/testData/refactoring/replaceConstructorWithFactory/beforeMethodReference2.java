import java.util.function.Supplier;

class Precarious {
  <caret>Precarious(String s) {}
}
class User {
  static void main() {
    Function<String, Precarious> supplier = Precarious::new;
    Precarious precarious = new Precarious("");
    new Precarious("") {};
    Precarious[] precariouses = new Precarious[10];
  }
}