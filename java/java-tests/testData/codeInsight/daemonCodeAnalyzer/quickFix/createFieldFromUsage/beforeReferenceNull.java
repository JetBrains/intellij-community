// "Create constant field 'MODULE' in 'Usage'" "true"
class Usage {
  void usage() {
    switch (null) {
      case CLASS -> symbols.add(1);
      case INTERFACE -> symbols.add(2);
      case <caret>MODULE -> symbols.add(42);
    }
  }
}

