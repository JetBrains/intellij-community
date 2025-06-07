/// forward references
import java.io.*;


public class a  {

  {
    int i;
    if (<error descr="Cannot read value of field 'FI' before the field's definition">FI</error> == 4) i = 5;
  }
  final int FI = 4;

  int k = 1 + <error descr="Cannot read value of field 'ki' before the field's definition">ki</error>;
  int ki;

  final int fi5 = <error descr="Cannot read value of field 'fi5' from inside the field's definition">fi5</error> + 1;
}

class a1 {
 static int i = <error descr="Cannot read value of field 'j' before the field's definition">j</error> + 2;
 static int j = 4;
}
class a2 {
 static { i = <error descr="Cannot read value of field 'j' before the field's definition">j</error> + 2; }
 static int i, j;
 static { j = 4; }
}

// pasted from JLS 8.3.2.3
class UseBeforeDeclaration {
 static {
  x = 100; // ok - assignment
  int y = <error descr="Cannot read value of field 'x' before the field's definition">x</error> + 1; // error - read before declaration
  int v = x = 3; // ok - x at left hand side of assignment
  int z = UseBeforeDeclaration.x * 2;	// ok - not accessed via simple name
  Object o = new Object(){ 
     void foo(){x++;} // ok - occurs in a different class
     {x++;} // ok - occurs in a different class
   };
  }
  {
   j = 200; // ok - assignment
   j = <error descr="Cannot read value of field 'j' before the field's definition">j</error> + 1; // error - right hand side reads before declaration
   int k = j = <error descr="Cannot read value of field 'j' before the field's definition">j</error> + 1;
   int n = j = 300; // ok - j at left hand side of assignment
   int h = <error descr="Cannot read value of field 'j' before the field's definition">j</error>++; // error - read before declaration
   int l = this.j * 3; // ok - not accessed via simple name
   Object o = new Object(){ 
     void foo(){j++;} // ok - occurs in a different class
     { j = j + 1;} // ok - occurs in a different class
   };
  }

  int w = x= 3; // ok - x at left hand side of assignment
  int p = x; // ok - instance initializers may access static fields
  static int u = (new Object(){int bar(){return x;}}).bar();  // ok - occurs in a different class
  static int x;
  int m = j = 4; // ok - j at left hand side of assignment
  int o = (new Object(){int bar(){return j;}}).bar();   // ok - occurs in a different class
  int j;
}

class a3 {
    static {
        <error descr="Cannot read value of field 'i' before the field's definition">i</error>+=1;
    }
    static int j=<error descr="Cannot read value of field 'i' before the field's definition">i</error>+=1;
    static int i=0;
    {
        <error descr="Cannot read value of field 'k' before the field's definition">k</error>+=1;
    }
    int n=<error descr="Cannot read value of field 'k' before the field's definition">k</error>+=1;
    int k=0;
}
