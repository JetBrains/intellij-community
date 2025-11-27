class Sweet {}
class Sour extends Sweet {
  Sour() {
  }
}
class Salty extends Sour {
  Salty() {
    super();
  }
}
class Bitter extends Salty {

  public static void main(String[] args) {
    new Bitter();
    new Bitter();
  }
}