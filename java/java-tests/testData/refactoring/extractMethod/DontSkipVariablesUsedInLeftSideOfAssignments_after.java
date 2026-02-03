
import java.util.List;

class Test {

  boolean m(Object[] parsed, String[] parameters, List<String> args) {
    int i = 1;
      if (newMethod(parsed, parameters[i], args.get(i), i)) return false;

      return false;
  }

    private boolean newMethod(Object[] parsed, String parameter1, String stringValue, int i) {
        String parameter = parameter1;
        try {
          parsed[i] = parseValue(stringValue, parameter.getClass());
        } catch (Exception e) {
            return true;
        }
        return false;
    }

    private static Object parseValue(String stringValue, Class type) {
    return "1";
  }

}