import java.util.*;

class C {
  public static interface GenericAgnosticProcessor {
    void processMap(Map map);
  }

  public static interface GenericAwareProcessor {
    void processMap(Map<String, String> map);
  }

  public static class TestProcessor implements GenericAwareProcessor, GenericAgnosticProcessor {
    @Override
    public void processMap(Map map) { }
  }

  public static void main(String[] args) {
    final TestProcessor testProcessor = new TestProcessor();
    testProcessor.<ref>processMap(new HashMap());
  }
}
