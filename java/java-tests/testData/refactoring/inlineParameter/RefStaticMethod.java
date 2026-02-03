public class StaticRefMove {
  public static int init() {
    return 1;
  }

  public void context() {
    StaticRefMoveSubject v = new StaticRefMoveSubject();
    v.subject(init());
  }
}

class StaticRefMoveSubject {
  public void subject(int <caret>subj) {
    System.out.println(subj);
  }
}
