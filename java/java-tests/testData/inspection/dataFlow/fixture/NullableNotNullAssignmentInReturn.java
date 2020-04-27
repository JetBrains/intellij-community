import org.jetbrains.annotations.Nullable;
class Hello {
  @Nullable
  String something;
  @Nullable
  private String getAndCacheSomething() {
    if (something != null) {
      return something;
    }
    return something = getSomething();
  }
  @Nullable
  private String getAndCacheSomething2() {
    if (something != null) {
      return something;
    }
    something = getSomething();
    return something;
  }
  @Nullable
  String getSomething() {
    return null;
  }
}