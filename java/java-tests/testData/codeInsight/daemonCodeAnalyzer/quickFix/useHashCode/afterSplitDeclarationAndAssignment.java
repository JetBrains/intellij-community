// "Replace with 'Double.hashCode()'" "true-preview"

class X {
  private String s;
  private double d;

  @Override
  public int hashCode() {
    int result;
      result = s != null ? s.hashCode() : 0;
      result = 31 * result + Double.hashCode(d);
    return result;
  }
}