// "Convert to a record" "true"
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
    return Objects.hash(booleanValue, charValue, stringValue, longValue, floatValue, doubleValue, arrayValue);
  }
}