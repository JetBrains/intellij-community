public class Animal {

  public static void main(String[] args) {
    Animal animal = new <caret>Animal();
  }
}
class OneLeggedDog extends Animal {
  OneLeggedDog() {
  }
}