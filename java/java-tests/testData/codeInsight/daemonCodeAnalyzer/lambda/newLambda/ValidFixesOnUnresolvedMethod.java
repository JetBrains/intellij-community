class MyTest {
  void m(String[] refInfos){
      refInfos = <error descr="Cannot resolve method 'unresolved' in 'MyTest'">unresolved</error>(refInfos, refInfo -> {
        refInfo = n(refInfo);
        return refInfo;
      });
  }
  private static String n(String refInfo) {
    return refInfo;
  }
}