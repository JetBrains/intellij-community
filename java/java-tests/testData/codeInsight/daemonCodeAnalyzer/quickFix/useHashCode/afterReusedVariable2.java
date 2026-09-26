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
    temp = Double.doubleToLongBits(d1);
    result = 31 * result + (int)(temp ^ (temp >>> 32));
      result = 31 * result + Double.hashCode(d2);
    return result;
  }
}