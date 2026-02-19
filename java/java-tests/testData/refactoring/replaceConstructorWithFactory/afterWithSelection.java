public class Main {

  private String s;
  private int i;

  private Main() {

  }
  public Main(String s, int i) {
    this.s = s;
    this.i = i;
  }

    public static Main newMain() {
        return new Main();
    }
}