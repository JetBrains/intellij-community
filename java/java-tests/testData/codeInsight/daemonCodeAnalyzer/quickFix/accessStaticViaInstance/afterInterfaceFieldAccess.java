// "Qualify static 'NAME' access with reference to class 'Grotesk'" "true-preview"
class Dreadful implements Grotesk {
  void x() {
    System.out.println(Grotesk.NAME);
  }
}
interface Grotesk {
  String NAME = "Grotesk";
}