// "Convert to a record" "true"
class <caret>Test {
  final double[] arrayValue;

  @Override
  public int hashCode() {
    int result = 17;

    result = 37 * result + Arrays.hashCode(arrayValue);

    return result;
  }
}