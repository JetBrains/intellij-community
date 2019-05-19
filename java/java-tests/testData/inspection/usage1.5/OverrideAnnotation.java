import java.util.Map;

public abstract class OverrideAnnotation implements Map<String, String> {
  @<error descr="Usage of API documented as @since 1.8+">Override</error>
  public String getOrDefault(Object key, String defaultValue) {
    return null;
  }
}

