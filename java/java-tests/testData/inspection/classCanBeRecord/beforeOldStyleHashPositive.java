// "Convert to record class" "true-preview"
class <caret>Test {
  final boolean booleanValue;
  final char charValue;
  final String stringValue;
  final long longValue;
  final float floatValue;
  final double doubleValue;
  final double[] arrayValue;

  @Override
  public int hashCode() {
    int result = 17;

    result = 37 * result + (booleanValue ? 1 : 0);
    result = 37 * result + (int) charValue;
    result = 37 * result + (stringValue == null ? 0 : stringValue.hashCode());
    result = 37 * result + (int) (longValue - (longValue >>> 32));
    result = 37 * result + Float.floatToIntBits(floatValue);
    result = 37 * result + (int) (Double.doubleToLongBits(doubleValue) - (Double.doubleToLongBits(doubleValue) >>> 32));

    result = 37 * result + arrayValue.hashCode();

    return result;
  }
}
