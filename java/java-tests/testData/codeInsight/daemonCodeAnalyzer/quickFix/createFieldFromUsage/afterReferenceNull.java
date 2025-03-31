// "Create constant field 'MODULE' in 'Usage'" "true"
class Usage {

    private static final Object MODULE = ;

    void usage() {
    switch (type) {
      case CLASS -> symbols.add(1);
      case INTERFACE -> symbols.add(2);
      case MODULE -> symbols.add(42);
    }
  }
}

