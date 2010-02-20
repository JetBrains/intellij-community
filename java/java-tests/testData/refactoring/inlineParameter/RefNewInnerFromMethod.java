public class Subject {
    private int myInt;

    public void withClass(Object <caret>o) {
        myInt += o.hashCode();
    }

    private void oper() throws IOException {
      Subject subj = new Subject();
      class Local {
      }
      subj.withClass(new Local());
    }


}