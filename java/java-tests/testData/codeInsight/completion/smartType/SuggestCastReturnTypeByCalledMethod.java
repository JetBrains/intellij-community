public class Main {

  public Object main(Object o) {
    return ((<caret>) o).zoo();
  }
  
  void zoo() {}

}
