public class Main {

  public Object main(Object o) {
    return ((Main<caret>) o).zoo();
  }
  
  void zoo() {}

}
