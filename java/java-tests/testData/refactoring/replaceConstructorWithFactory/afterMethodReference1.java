import java.util.function.Supplier;

class Precarious {
    Precarious() {
    }

    static Precarious createPrecarious() {
        return new Precarious();
    }
}
class User {
  static void main() {
    Supplier<Precarious> supplier = Precarious::createPrecarious;
    Precarious precarious = Precarious.createPrecarious();
    new Precarious() {};
    Precarious[] precariouses = new Precarious[10];
  }
}