// "Replace 'that' with pattern variable" "true"
class PatternInner<T> {

  class Basis {
    String key;

    private PatternInner<T> outer() {
      return PatternInner.this;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof @SuppressWarnings("rawtypes")PatternInner.Basis that)) {
        return false;
      }
        return this.outer() == that.outer() && this.key.equals(that.key);
    }
  }
}