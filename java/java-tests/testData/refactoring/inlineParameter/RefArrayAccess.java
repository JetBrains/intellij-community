public class Subject {
  private int myInt;

  public void withArray(int[] <caret>pia) {
    myInt += pia[0];
  }
}

class User {
  private void oper() {
    Subject subj = new Subject();
    int[] ia = new int[]{0, 1};
    ia[0] = 2;
    subj.withArray(ia);
  }
}
