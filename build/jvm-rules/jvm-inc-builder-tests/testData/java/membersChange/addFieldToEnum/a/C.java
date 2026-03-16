public class C {
  public void process(A param) {
    switch(param) {
      case CONST_1 :
        processImpl();
        break;
      case CONST_2 :
        processImpl();
        break;
    }
  }

  private void processImpl() {}
}
