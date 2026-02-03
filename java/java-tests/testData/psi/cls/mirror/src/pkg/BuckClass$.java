package pkg;

class BuckClass$ {
  BuckClass$() {
    System.out.println(this);
  }

  static BuckClass$ getInstance() {
    return new BuckClass$();
  }
}
