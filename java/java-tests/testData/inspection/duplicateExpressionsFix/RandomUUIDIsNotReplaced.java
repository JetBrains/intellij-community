import java.util.UUID;

class C {
  private staticvoid test() {
    String s1 = <caret>String.valueOf(UUID.randomUUID().getMostSignificantBits());
    String s2 = String.valueOf(UUID.randomUUID().getMostSignificantBits());
  }
}