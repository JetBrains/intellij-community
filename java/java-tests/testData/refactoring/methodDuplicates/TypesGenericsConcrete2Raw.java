import java.util.List;

class Types {
  private List<String> myList;

  public void <caret>method(List v) {
    v.clear();
  }

  public void context() {
    myList.clear();
  }
}