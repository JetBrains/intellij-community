// "Add constructor parameters" "true"
class A {

  private final LibraryManager libraryManager;
  private final DependencyManager dependencyManager;

  A(final LibraryManager manager, final DependencyManager dependencyManager) {
      this.libraryManager = manager;
      this.dependencyManager = dependencyManager;
  }


  private static class LibraryManager {}
  private static class DependencyManager {}
}