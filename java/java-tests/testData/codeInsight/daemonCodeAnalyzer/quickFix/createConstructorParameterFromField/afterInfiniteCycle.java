// "Add constructor parameter" "true"
class Main{
  private String a;

  public Main(Integer a, String a1) {
    this(null, a1);
  }


  public Main(Integer a, Integer b, String a1) {
      this.a = a1;
  }
}