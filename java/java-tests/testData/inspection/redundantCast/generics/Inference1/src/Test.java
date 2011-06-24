import java.util.Map;
class Test2 {
    public String s;
  public void maina(Object key, Map parameters) {
      s = ((String[]) parameters.get(key))[0];
  }
}

public class Test {
    static class SomeClass {
        public <T> T getX() {
            return null;
        }
    }

    public static void main(String[] args) {
        //cast is needed for 'String' to be infered!
        System.getProperty((String)new SomeClass().getX());
    }
}
