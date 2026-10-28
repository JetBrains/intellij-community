import java.util.function.Supplier;

class Precarious<caret> {
}
class User {
  static void main() {
    Supplier<Precarious> supplier = Precarious::new;
    Precarious precarious = new Precarious();
    new Precarious() {};
    Precarious[] precariouses = new Precarious[10];
  }
}