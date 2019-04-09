import java.util.List;

public class MethodFromCycle {
  public MethodFromCycle(Object o) {
  }

  private Object reduce(List<List<String>> list, List<String> input) {
    for (List<String> element : list) {

        NewMethodResult x = newMethod(element, input);
        if (x.exitKey == 1) return x.returnResult;

    }
    return null;
  }

    NewMethodResult newMethod(List<String> element, List<String> input) {
        for (String some : element) {
          List<String> tag = input;
          List<String> oldValue = tag;
            return new NewMethodResult((1 /* exit key */), new MethodFromCycle(withParameters(oldValue, tag)));
        }
        return new NewMethodResult((-1 /* exit key */), (null /* missing value */));
    }

    static class NewMethodResult {
        private int exitKey;
        private Object returnResult;

        public NewMethodResult(int exitKey, Object returnResult) {
            this.exitKey = exitKey;
            this.returnResult = returnResult;
        }
    }

    public Object withParameters(Object s, Object s1) {
    return null;
  }
}