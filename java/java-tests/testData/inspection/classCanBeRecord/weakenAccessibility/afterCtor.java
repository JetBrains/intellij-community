// "Convert to record class" "GENERIC_ERROR_OR_WARNING"
record R(int first) {
}

class AA{
  public static void main(String[] args) {
    R r = new R(1);
    System.out.println(r.first());
  }
}
