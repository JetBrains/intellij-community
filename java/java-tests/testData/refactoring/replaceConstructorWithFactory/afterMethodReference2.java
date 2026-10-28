import java.util.function.Supplier;

class Precarious {
  Precarious(String s) {}

    static Precarious createPrecarious(String s) {
        return new Precarious(s);
    }
}
class User {
  static void main() {
    Function<String, Precarious> supplier = Precarious::createPrecarious;
    Precarious precarious = Precarious.createPrecarious("");
    new Precarious("") {};
    Precarious[] precariouses = new Precarious[10];
  }
}