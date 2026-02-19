// "Convert to record class" "true"
// no "true-preview" above because of IDEA-369873
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
