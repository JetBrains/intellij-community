class MyTest {
  void m(String[] refInfos){
    refInfos = <error descr="Cannot resolve method 'unresolved' in 'MyTest'">unresolved</error>(refInfos, refInfo -> {
      refInfo = refInfo.<error descr="Cannot resolve method 'replaceAll(String, String)'">replaceAll</error>("a", "b");
      refInfo = n(refInfo);
      return refInfo;
    });
  }
  private static String n(String refInfo) {
    return refInfo;
  }
}