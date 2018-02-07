// "Extract common part from if " "false"

public class Main {

  static class Assert {
    static Matcher assertThat(int i) {}
  }

  static class Matcher {
    Matcher isGreaterThan(int i)
    Matcher isLowerThan(int i)
  }

  boolean getCondition() {
    return true;
  }

  public void main() {
    boolean condition = getCondition();
    int a = 12;
    int b = 44;
    int c = 42;
    int d = 45;
    StringBuilder builder = new StringBuilder();
    if<caret>(condition) {
      assertThat(a).isGreaterThan(120);
      assertThat(b).isGreaterThan(10);
      assertThat(c).isGreaterThan(1000).isLowerThan(12);
      assertThat(d).isGreaterThan(100);
    } else {
      assertThat(a).isGreaterThan(12);
      assertThat(a).isGreaterThan(240);
      assertThat(b).isGreaterThan(60);
      assertThat(c).isGreaterThan(10).isLowerThan(12);
      assertThat(d).isGreaterThan(100);
    }
  }
}