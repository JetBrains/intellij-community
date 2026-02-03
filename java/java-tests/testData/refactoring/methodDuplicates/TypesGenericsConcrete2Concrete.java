import java.util.List;

class Types {
  private List<String> myList;

  public String <caret>method(List<String> v) {
    return v.get(0);
  }

  public void context() {
    String s = myList.get(0);
  }
}