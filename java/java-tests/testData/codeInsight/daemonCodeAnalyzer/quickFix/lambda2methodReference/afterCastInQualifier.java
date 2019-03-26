// "Replace lambda with method reference" "true"
class Bar extends Random {
  public void test(Object obj) {
    Runnable r = ((String) obj)::trim;
  }
}