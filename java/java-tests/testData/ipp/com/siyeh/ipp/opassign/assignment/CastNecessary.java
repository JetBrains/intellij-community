class CastNecessary {
  static final Byte READ_BIT = 4;
  void m() {
    byte result = 0;
    result += <caret>read ? READ_BIT : 0;
  }
}