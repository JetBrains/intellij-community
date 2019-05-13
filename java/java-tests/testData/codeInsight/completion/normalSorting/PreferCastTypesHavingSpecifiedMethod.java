public class MainClass1 {

  public void main(Object o) {
    ((Ma<caret>) o).zoo();
  }

  void zoo() {}

}

class MainClass2 {
  void zoo() {}
}

class Maa {
  void zoo2() {}
}
