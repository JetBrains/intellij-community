class User {
  public class Subject {
    private int myInt;

    public void withClass(Object <caret>o) {
      myInt += o.hashCode();
    }
  }

  private void oper() {
    Subject subj = new Subject();
    subj.withClass(this);
  }
}

