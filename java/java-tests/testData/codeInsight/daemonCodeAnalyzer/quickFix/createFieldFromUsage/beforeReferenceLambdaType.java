// "Create constant field 'MODULE' in 'Usage'" "true"
class Usage {

  void usage() {
    var type = ()-> System.out.println("");
    switch (type) {
      case CLASS -> symbols.add(1);
      case INTERFACE -> symbols.add(2);
      case <caret>MODULE -> symbols.add(42);
    }
  }
}

