// "Create constant field 'MODULE' in 'Usage'" "true"
class Usage {

    private static final  MODULE = ;

    void usage() {
    var type = ()-> System.out.println("");
    switch (type) {
      case CLASS -> symbols.add(1);
      case INTERFACE -> symbols.add(2);
      case MODULE -> symbols.add(42);
    }
  }
}

