// "Collapse into loop" "false"
class X {
  public int hashCode() {
    int result = 1;
    int other = 3;
    <selection>result = 2 + other;
    result = 2 + result;</selection>
    return result;
  }
}