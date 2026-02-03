// "Replace with 'Double.hashCode()'" "true-preview"

class X {
  private String s;
  private double d;

  @Override
  public int hashCode() {
    int result;
    long temp;
    result = s != null ? s.hashCode() : 0;
    temp = Double.doubleToLongBits(d);
    result = 31 * result + (int)<caret>(temp ^ (temp >>> 32));
    return result;
  }
}