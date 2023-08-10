class PolyadicExpression {
  double calc(long interval, long delta) {
    return interval <= 0 ? 0 : delta<caret> / interval / 10.0;
  }
}