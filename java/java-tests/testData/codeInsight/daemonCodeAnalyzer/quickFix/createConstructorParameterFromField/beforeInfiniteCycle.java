// "Add constructor parameter" "true"
class Main{
  private String a<caret>;

  public Main(Integer a) {
    this(null);
  }


  public Main(Integer a, Integer b) {
  }
}