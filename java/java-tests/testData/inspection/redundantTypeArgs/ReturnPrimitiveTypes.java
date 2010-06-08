import java.util.Map;
class Test {
  Map someMap;

  public int getGetBar() {
    return this.<Integer>getFoo("numberOfConfigurations");
  }

  protected <T> T getFoo(String key) {
    return (T)someMap.get(key);
  }
}