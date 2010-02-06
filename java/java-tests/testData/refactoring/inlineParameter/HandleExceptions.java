import java.io.*;
public class Subject {
  private int myInt;

  public void withClass(Object <caret>o) {
    myInt += o.hashCode();
  }
}

class User {
  private void oper() throws IOException {
    Subject subj = new Subject();
    subj.withClass(new ThirdParty(false));
  }
}

class ThirdParty {
  public ThirdParty() {
  }

  public ThirdParty(boolean b) throws IOException {
    if (b) {
      throw new IOException();
    }
  }
}
