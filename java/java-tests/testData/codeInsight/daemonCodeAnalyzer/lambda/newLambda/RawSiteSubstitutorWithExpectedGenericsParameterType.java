
interface Processor<T> {

  void process(T t);
}

class MySymbol<T extends String> {

  void processSameSymbols(Processor<MySymbol> processor) {}

  T locateDefinition() {
    return (T) "";
  }

  void findUsages(MySymbol s) {
    s.processSameSymbols(symbol -> {
      String definition = symbol.<error descr="Cannot resolve method 'locateDefinition()'">locateDefinition</error>();
    });
  }
}
