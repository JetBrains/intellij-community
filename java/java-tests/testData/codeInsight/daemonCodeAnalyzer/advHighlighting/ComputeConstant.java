class A_CR{
  public static final int CONST = <error descr="Cannot resolve symbol 'CONSTX'">CONSTX</error>;

  {
    switch(0){
      case <error descr="Constant expression required">CONST</error>:
    }
  }
}

class AClass {
    private static final String SPACE = " ";
    class s {
        private static final String RET_VAL =
                    "Roger" +
                    SPACE +
                    SPACE + // comment and uncomment this line
                    " Dodger";
    }
}