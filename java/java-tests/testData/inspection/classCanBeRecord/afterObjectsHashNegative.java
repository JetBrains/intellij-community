// "Convert to record class" "true-preview"
import java.util.Objects;

record Test(boolean booleanValue, char charValue, String stringValue, long longValue, float floatValue,
            double doubleValue, double[] arrayValue) {
    @Override
    public int hashCode() {
        return Objects.hash(booleanValue, stringValue, longValue, floatValue, doubleValue, arrayValue);
    }
}
