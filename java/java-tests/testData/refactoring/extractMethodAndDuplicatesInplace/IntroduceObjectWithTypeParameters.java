public class Test<R> {

  private R getR() {
    return null;
  }

  public <T extends CharSequence> void main(String[] args, T param) {
    <selection>T t = param;
    R r = getR();
    System.out.println();</selection>

    System.out.println("Custom(" + t + ", " + r + ")");
  }
}