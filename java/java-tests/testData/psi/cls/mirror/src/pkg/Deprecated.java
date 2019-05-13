package pkg;

class Deprecated {

  /** @deprecated don't use */
  int byComment;

  @java.lang.Deprecated
  int byAnno;

  /** @deprecated don't use */
  void byComment() { }

  @java.lang.Deprecated
  void byAnno() { }

  /** @deprecated don't use */
  static class ByComment { }

  @java.lang.Deprecated
  static class ByAnno { }

}