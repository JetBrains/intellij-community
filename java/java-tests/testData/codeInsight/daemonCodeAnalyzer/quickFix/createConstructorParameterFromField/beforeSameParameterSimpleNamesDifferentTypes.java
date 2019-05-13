// "Add constructor parameters" "true"
class A {

  private final LibraryManager libr<caret>aryManager;
  private final DependencyManager dependencyManager;

  A() {}


  private static class LibraryManager {}
  private static class DependencyManager {}
}