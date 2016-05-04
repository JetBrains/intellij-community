
class Cat {}
class DomesticCat extends Cat {}

class Test {
  public static void main(String[] args) {
    Cat c<caret>at = new DomesticCat();
    petCat(cat);
  }

  static void petCat(Cat cat) {
    System.out.println("A Cat");
  }

  static void petCat(DomesticCat domesticCat) {
    System.out.println("A DomesticCat");
  }
}