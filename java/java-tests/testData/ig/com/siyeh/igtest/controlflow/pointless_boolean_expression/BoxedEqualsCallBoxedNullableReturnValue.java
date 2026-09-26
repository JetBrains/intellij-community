import org.jetbrains.annotations.Nullable;

class Boxed {
  public final @Nullable Boolean isRealTimeProtectionEnabled_1() {
    Boolean rtProtection = Math.random() > 0.33 ? (Math.random() > 0.5) : null;
    return Boolean.TRUE.equals(rtProtection);
  }

  public final @Nullable Boolean isRealTimeProtectionEnabled_2() {
    @Nullable Boolean rtProtection = Math.random() > 0.33 ? (Math.random() > 0.5) : null;
    return Boolean.TRUE.equals(rtProtection);
  }
}
