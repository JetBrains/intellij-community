class MyTest {
  void m(String[] refInfos){
    refInfos = <error descr="Cannot resolve method 'unresolved' in 'MyTest'">unresolved</error>(refInfos, refInfo -> {
      refInfo = refInfo.<error descr="Cannot resolve method 'replaceAll(String, String)'">replaceAll</error>("a", "b");
      refInfo = n<error descr="'n(java.lang.String)' in 'MyTest' cannot be applied to '(<lambda parameter>)'">(refInfo)</error>;
      return refInfo;
    });
  }
  private static String n(String refInfo) {
    return refInfo;
  }
}