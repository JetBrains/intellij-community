// "Convert to record class" "GENERIC_ERROR_OR_WARNING"
class <caret>R {
  final int first;

  private R(int first) {
    this.first = first;
  }
}

class AA{
  public static void main(String[] args) {
    R r = new R(1);
    System.out.println(r.first);
  }
}
