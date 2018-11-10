class CleanupIf {
  private final int expirationDay = 0;

  int check(CleanupIf other, boolean ignoreExpirationDay) {
    if (<warning descr="Condition '!ignoreExpirationDay && expirationDay != other.expirationDay' is always 'false'">!ignoreExpirationDay &&
        <warning descr="Condition 'expirationDay != other.expirationDay' is always 'false' when reached">expirationDay !=<caret> other.expirationDay</warning></warning>)
      return expirationDay < other.expirationDay ? -1 : 1;
    return 0;
  }
}