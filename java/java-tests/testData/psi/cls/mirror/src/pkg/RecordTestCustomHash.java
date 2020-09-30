package pkg;

public record RecordTestCustomHash(int x, int y) {
  public int hashCode() {
    return x + y;
  }
}