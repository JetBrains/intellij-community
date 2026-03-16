public interface GenericInterfaceWithMarkdown<A> {

  /// For some reason, markdown comments break
  /// generated implementation in the current release (2025.2)
  /// but not on master, so here's a test
  A functionWithMarkdown();
}

class DefaultImpl implements GenericInterfaceWithMarkdown<String> {
    <caret>
}