class PolyadicExpression {
  double calc(long interval, long delta) {
    return interval <= 0 ? 0 : delta<caret> * 2 / interval / 10.0;
  }
}