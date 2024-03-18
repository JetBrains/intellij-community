// "Create property 'next' in 'InnerClass in MainClass'" "true-preview"
public class MainClass {
  void foo() {
    int counter = InnerClass.<caret>getNext();
  }

  private class InnerClass {

  }
}