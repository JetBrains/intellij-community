class MyTest {
  void m(String[] refInfos){
      refInfos = <error descr="Cannot resolve method 'unresolved(java.lang.String[], <lambda expression>)'">unresolved</error>(refInfos, refInfo -> {
        refInfo = n<error descr="'n(java.lang.String)' in 'MyTest' cannot be applied to '(<lambda parameter>)'">(refInfo)</error>;
        return refInfo;
      });
  }
  private static String n(String refInfo) {
    return refInfo;
  }
}