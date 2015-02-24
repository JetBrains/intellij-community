import java.util.Map;

public abstract class Test implements Map<String, String> {
  @Override
  public String getOrDefault(Object key, String defaultValue) {
    return null;
  }
}

