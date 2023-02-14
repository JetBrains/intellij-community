public record R(int i) {
  public R(int i, String s) {
    this(1);
  }

  public static void main(String[]args){
    new R(2, "asdf");
  }
}