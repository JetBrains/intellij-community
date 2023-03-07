class X {
  String string() {
    return "heavy" + "⚡️" + "metal";
  }

  String x() {
    return "🤘" + string<caret>() + "🤘";
  }
}