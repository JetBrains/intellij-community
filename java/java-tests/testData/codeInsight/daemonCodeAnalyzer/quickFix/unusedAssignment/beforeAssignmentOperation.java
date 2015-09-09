// "Remove redundant assignment" "true"
class A {
  public String getContexts(final String env) {
    String contexts = "a";
    if ("dev".equals(env)) {
      return cont<caret>exts += ",b";
    }
    return contexts;
  }
}