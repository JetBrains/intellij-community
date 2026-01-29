public class D {
  public int process(A param) {
    return switch(param) {
      case CONST_1 -> 1;
      case CONST_2 -> 2;
    };
  }
}
