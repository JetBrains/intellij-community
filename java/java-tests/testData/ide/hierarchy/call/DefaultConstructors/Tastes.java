class Sweet {}
class Sour extends Sweet {}
class Salty extends Sour {}
class Bitter extends Salty {

  public static void main(String[] args) {
    new Bitter();
  }
}