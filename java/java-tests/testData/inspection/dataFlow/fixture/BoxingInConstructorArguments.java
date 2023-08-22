
class Prop<T> {
  private final T defaultValue;
  private final Key key;

  public Prop(Key key, T defaultValue) {
    assert defaultValue != null;
    this.key = key;
    this.defaultValue = defaultValue;
  }

  public static void main(String[] args) {
    Prop<Integer> prop = new Prop<>(Key.EXAMPLE, 1);
  }
}

enum Key {EXAMPLE}