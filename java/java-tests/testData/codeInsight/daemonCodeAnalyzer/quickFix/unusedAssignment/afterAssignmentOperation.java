// "Remove redundant assignment" "true-preview"
class A {
  public String getContexts(final String env) {
    String contexts = "a";
    if ("dev".equals(env)) {
      return contexts + ",b";
    }
    return contexts;
  }
}