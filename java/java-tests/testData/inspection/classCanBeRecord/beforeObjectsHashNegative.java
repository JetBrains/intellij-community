// "Convert to record class" "true-preview"
import java.util.Objects;

class <caret>Test {
  final boolean booleanValue;
  final  char charValue;
  final String stringValue;
  final long longValue;
  final float floatValue;
  final double doubleValue;
  final double[] arrayValue;

  @Override
  public int hashCode() {
    return Objects.hash(booleanValue, stringValue, longValue, floatValue, doubleValue, arrayValue);
  }
}
