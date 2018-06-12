// invalid expressions
public class a12 {
  static int i;

  int f(int i) {
    <error descr="Not a statement">1;</error>
    <error descr="Not a statement">1==2;</error>
    <error descr="Not a statement">1==2 ? 1 : 3;</error>
    <error descr="Not a statement">f(1) == 3;</error>
    <error descr="Not a statement">1,2;</error>
    <error descr="Not a statement">~1;</error>
    <error descr="Not a statement">!(1==2);</error>
    <error descr="Not a statement">new int[0];</error>
    <error descr="Not a statement">new a12[10];</error>
    <error descr="Not a statement">new a12[]{};</error>
    <error descr="Not a statement">new int[]{1};</error>
    <error descr="Not a statement">new String[]{new String()};</error>
    if (i==1)
      <error descr="Declaration not allowed here">String s00 = "";</error>
    for (;;)
      <error descr="Declaration not allowed here">String s01 = "";</error>

    
    for (<error descr="Not a statement">1==2,i=3;</error> i<3; <error descr="Not a statement">!(1==2)</error>);

    label2:
        <error descr="Not a statement">int ksdfgdf;</error>


    int i1=3, g=5;
    g++;
    ++g;
    for (int k=1,l=3; k<3; k++,l--) {;};
    class s {}
    new s() { void f(){}};
    return 3;
  }



  //////////////////////////
  void f() {
    a12 a[] = new a12[4];
    int[] ai = null;

    <error descr="Variable expected">5</error> = 5;
    <error descr="Variable expected">5</error>++;
    ++<error descr="Variable expected">5</error>;
    <error descr="Variable expected">5</error> += 5;

    <error descr="Cannot resolve method 'foo123Unresolved(?)'">foo123Unresolved</error>(<error descr="Expression expected">String</error>);
    <error descr="Cannot resolve method 'foo123Unresolved(?)'">foo123Unresolved</error>(<error descr="Cannot resolve symbol 'xxxx'">xxxx</error>);

    <error descr="Cannot resolve method 'xxxxxx(?)'">xxxxxx</error>(<error descr="Cannot resolve symbol 'xxxxxx'">xxxxxx</error>);

    // incomplete code should not cause 'expr expected'
    Object<error descr="';' expected"> </error>


    <error descr="Array type expected; found: 'int'">4</error>[1] = 5;
    a[<error descr="Incompatible types. Found: 'java.lang.String', required: 'int'">"d"</error>] = null;

    <error descr="Array type expected; found: 'a12'">a[0]</error>[1] = 5;
    <error descr="Array type expected; found: 'int'">i</error>[0] = 5;
    i = ai[<error descr="Incompatible types. Found: 'a12', required: 'int'">a[1]</error>];

    final a12[] a12a = new a12[<error descr="Incompatible types. Found: 'null', required: 'int'">{null}</error>];
    int[] iarr = new int[<error descr="Incompatible types. Found: 'null', required: 'int'">{0}</error>];


    new String[<error descr="Incompatible types. Found: 'java.lang.String', required: 'int'">"d"</error>];
    new String[<error descr="Incompatible types. Found: 'double', required: 'int'">1.1</error>]
              [<error descr="Incompatible types. Found: 'a12', required: 'int'">this</error>];

    a[0] = null;
    (a)[(1)].i = 3;
    (( i) ) = (5);
    a12.i = 0;
    arr()[0] = 1;
    new int[] { 1,3} [0] = 2;
    Object[] var = null;
    i = var.length;

    // array initializers
    var = <error descr="Array initializer is not allowed here">{ null, null }</error>;
    Object var1 = <error descr="Array initializer is not allowed here">{ null, null }</error>;
    int[] var2 = { 1,2 };
    var2 = new int[] { 2, 4};
    int[][] ii2 = { { 1,2}, {4} };

  }
  <error descr="Illegal type: 'void'">void</error>[] fv() {
     if (1==2) return new <error descr="Illegal type: 'void'">void</error>[0];
     return null;
  }

  int[] arr() { return new int[0]; }

  public <error descr="Invalid method declaration; return type required">foo</error>() {
  }

  {
    new Object() {
      <error descr="Invalid method declaration; return type required">Object</error>() {}
    };
  }

  // do not warn about illegal type in incomplete declarations (http://www.intellij.net/tracker/idea/viewSCR?publicId=9586)
  void foo<EOLError descr="';' expected"></EOLError>
}


//invalid arrays
class array {
  {
    int[] a1 =<error descr="Expression expected"> </error><error descr="Unexpected token">.</error>new <error descr="Cannot resolve symbol 'C'">C</error>[0];
    int[] a2 = {}.new <error descr="Cannot resolve symbol 'D'">D</error>[0];
    int[] a3 = t -><error descr="'{' expected"> </error>.new <error descr="Cannot resolve symbol 'E'">E</error>[0];
    int[] a4 = <error descr="Cannot resolve symbol 'a'">a</error>::<error descr="';' expected"><error descr="Identifier expected"><error descr="Unexpected token">.</error></error></error>new <error descr="Cannot resolve symbol 'F'">F</error>[0];
  }
}