import java.util.Objects;
class Main {
  final String str;
  public Main(String str) {
    this.str = Objects.requireNonNull(str);
  }
  public String getStr() {
    if (<warning descr="Condition 'str == null' is always 'false'">str == null</warning>) {

    }
    return str;
  }
}
