class c {
  void f() {
    //---- switch --------------------------------------------------------
    switch (<error descr="Incompatible types. Found: 'java.lang.String', required: 'byte, char, short or int'">"s"</error>) 
    {default:}
    byte bt = 0;
    switch (bt) {
      case <error descr="Incompatible types. Found: 'java.lang.String', required: 'byte'">"S"</error>: break;
      case <error descr="Incompatible types. Found: 'long', required: 'byte'">10L</error>: break;
      case <error descr="Incompatible types. Found: 'boolean', required: 'byte'">true</error>: break;
      case <error descr="Incompatible types. Found: 'int', required: 'byte'">0xdfffffff</error>: break;
      case <error descr="Incompatible types. Found: 'char', required: 'byte'">'\udede'</error>: break;
      case <error descr="Incompatible types. Found: 'int', required: 'byte'">1280</error>: break;
      // assignable compatible to byte
      case 0: break;
      case 'c': break;
      case -1: break;
      case 127: break;
    }
    char ch = 'd';
    switch (ch) {
      case <error descr="Incompatible types. Found: 'java.lang.String', required: 'char'">"S"</error>: break;
      case <error descr="Incompatible types. Found: 'long', required: 'char'">10L</error>: break;
      case <error descr="Incompatible types. Found: 'boolean', required: 'char'">true</error>: break;
      case <error descr="Incompatible types. Found: 'int', required: 'char'">0xafffffff</error>: break;
      // assignable compatible to char
      case 0: break;
      case '\u4567': break;
      case 0xffff: break;
      case 255: break;
    }
    switch ('d') {default:}

    /// --------- incompatible types inside array initializer ----------


    int[] ia = new int[] {
      <error descr="Incompatible types. Found: 'java.lang.String', required: 'int'">"String"</error> 
      , <error descr="Incompatible types. Found: 'double', required: 'int'">3.4</error> 
    };

    String[] sa = { "s",
       <error descr="Incompatible types. Found: 'boolean', required: 'java.lang.String'">false</error> 
       , <error descr="Incompatible types. Found: 'char', required: 'java.lang.String'">'S'</error> 
    };

    String[] vas = null;
    Object o =  new int[<error descr="Incompatible types. Found: 'java.lang.String', required: 'int'">vas[0]</error>];

    int[] weird={<error descr="Illegal initializer for 'int'">{0}</error>};
    int[][] arrayInitializers = {{ }};
    int[][][] arrayInitializers2 = {{ }, {{}} };
    double[][] i2d = {{1}};
    char[][] i2c = {{1}};
    char foo = 0x0000; /* okay */
    char[] bar = { 1,2,3 }; /* still okay */
    char[][] baz = { { 1,2,3 } }; /* not okay in 4.5, okay in 4.0.x and for javac */


    /// -------- conditional operator
    int i8 = <error descr="Incompatible types. Found: 'java.lang.String', required: 'boolean'">"ff" + true</error> ? 1 : 2;
    int i9 = 1==2 ? 1 : <error descr="Incompatible types. Found: 'java.lang.String', required: 'int'">"ff" + true</error>;
    i9 = 1==2 ? 3 : <error descr="Incompatible types. Found: 'null', required: 'int'">null</error>;
    Object o9 = true ? 0 : <error descr="Incompatible types. Found: 'java.lang.Object', required: 'int'">new Object()</error>;



    final char ccons='0';
    short ssss=ccons;
    byte bbbbbb=ccons;
    // too big char to fit in short
    final char bigchar='\uffff';
    <error descr="Incompatible types. Found: 'char', required: 'short'">short sbig = bigchar;</error>
  }

  void g(boolean f, byte b) {
      byte c = '\n';
      <error descr="Incompatible types. Found: 'int', required: 'byte'">byte next = f ? b : '\n';</error>
  }
}