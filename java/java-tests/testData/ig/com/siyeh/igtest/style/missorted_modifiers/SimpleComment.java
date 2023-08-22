class SimpleComment {

  void foo(<warning descr="Missorted modifiers 'final @Deprecated'">final<caret></warning>/* comment */ @Deprecated String project) {

  }
}