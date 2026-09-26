// operators applicability
public class a {

  int f(int ik) {
    if (<error descr="Operator '<' cannot be applied to 'int', 'null'">1 < null</error>) {}
    if (<error descr="Operator '==' cannot be applied to 'null', 'char'">null == 'c'</error>) {}
    Object o = null;
    if (<error descr="Operator '>=' cannot be applied to 'double', 'java.lang.Object'">1.2 >= o</error>) {}
    if (<error descr="Operator '!=' cannot be applied to 'long', 'java.lang.String'">1L != "null"</error>) {}
    if (<error descr="Operator '==' cannot be applied to 'boolean', 'int'">(1==2) == 3</error>) {}

    int i = (<error descr="Operator '+' cannot be applied to 'int', 'null'">1 + null</error>);
    i = <error descr="Operator '/' cannot be applied to 'java.lang.Object', 'java.lang.Object'">o/o</error>;
    i = <error descr="Operator '-' cannot be applied to 'null', 'double'">null - 1.2</error>;
    i = <error descr="Operator '%' cannot be applied to 'boolean', 'int'">true % 4</error>;

    i = <error descr="Operator '<<' cannot be applied to 'int', 'java.lang.Object'">i << o</error>;
    i = <error descr="Operator '>>' cannot be applied to 'boolean', 'null'">(i==2) >> null</error>;
    i = <error descr="Operator '>>>' cannot be applied to 'int', 'double'">i >>> 2.2</error>;

    i = <error descr="Operator '&' cannot be applied to 'int', 'java.lang.Object'">i & o</error>;
    i = <error descr="Operator '|' cannot be applied to 'boolean', 'double'">true | 2.1</error>;
    i = <error descr="Operator '&&' cannot be applied to 'int', 'int'">2 && 3</error>;
    i = <error descr="Operator '||' cannot be applied to 'double', 'long'">3.8 || 2L</error>;
    i = <error descr="Operator '||' cannot be applied to 'null', 'java.lang.Object'">null || o</error>;

    i <error descr="Operator '|' cannot be applied to 'int', 'null'">|=</error> null;
    double d = 0;
    d <error descr="Operator '&' cannot be applied to 'double', 'int'">&=</error> i;
    o <error descr="Operator '/' cannot be applied to 'java.lang.Object', 'int'">/=</error> 3;


    String sss2 = <error descr="Operator '+' cannot be applied to 'java.lang.String', 'void'">"" + fvoid()</error>;
    int sss1 = <error descr="Operator '+' cannot be applied to 'void', 'int'">fvoid() + 2</error>;

    int ia[] = null;
    boolean b = 1==3 || 3 < '4' && (1>3.5) == (o == null) || false || (o == "d");
    b = (1 != 'f') == (3.4 >= 'x') && o!=null & (b | (3<4));
    i = i & 2 | i>>i ^ 15>>>4 & ~ia[i-- + (int)d] - (int)d;

    b |= (i &= 7) == 5 | (null == null);
    d *= (i -= 3) / 13.4;
    ia[0]++;
    ia[~i | (i+=(!b?2:i))] -= i + 3.3;
    
    // Object += String
    o += o + "string";

    return 0;
  }

  void fvoid() {}
  
  void unboxing(Byte b) {
    byte temp = <error descr="Operator '<<' cannot be applied to 'java.lang.Byte', 'int'">b << 4</error>;
  }

}

class Test
{
   public void test(TestB a)
   {
     if(a == this)
     {
       System.out.println("a is equals to this");
     }
   }

   public static interface TestB
   {
     public void bla();
   }
}
