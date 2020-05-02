
import java.util.List;

class Test {

  boolean m(Object[] parsed, String[] parameters, List<String> args) {
    int i = 1;
    <selection>String parameter = parameters[i];
    try {
      parsed[i] = parseValue(args.get(i), parameter.getClass());
    } catch (Exception e) {
      return false;
    }
    </selection>
    return false;
  }

  private static Object parseValue(String stringValue, Class type) {
    return "1";
  }

}