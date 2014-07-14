
import java.util.*;

class Test {
  {
    List<UnaryOperator<String>> a = asList(String::intern);
  }

  public static <Ta> List<Ta> asList(Ta a) {
    return null;
  }

  interface UnaryOperator<T> {
    T apply(T t);
  }
}

class TestVarargs {
  {
    List<UnaryOperator<String>> a = asList(String::intern);
  }

  public static <Ta> List<Ta> asList(Ta... a) {
    return null;
  }

  interface UnaryOperator<T> {
    T apply(T t);
  }
}