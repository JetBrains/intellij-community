// "Add constructor parameters" "true"
class A {

  private final LibraryManager libraryManager;
  private final DependencyManager dependencyManager;

  A(LibraryManager manager, DependencyManager dependencyManager) {
      this.libraryManager = manager;
      this.dependencyManager = dependencyManager;
  }


  private static class LibraryManager {}
  private static class DependencyManager {}
}