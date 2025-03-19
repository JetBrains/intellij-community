// "Replace with 'Double.hashCode()'" "true-preview"

class X {
  private String s;
  private double d1;
  private double d2;

  @Override
  public int hashCode() {
    int result;
    long temp;
    result = s != null ? s.hashCode() : 0;
      result = 31 * result + Double.hashCode(d1);
    temp = Double.doubleToLongBits(d2);
    result = 31 * result + (int)(temp ^ (temp >>> 32));
    return result;
  }
}