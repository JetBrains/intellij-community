// ternary operator

class a {
 void f1() {
    byte b = 4;
    int i = 2;
    boolean bo = false;
    long l = 5;
    float f = 5;
    double d = 45;

    String s;
    <error descr="Incompatible types. Found: 'int', required: 'java.lang.String'">s = bo ? 1 : 2</error>;
    <error descr="Incompatible types. Found: 'long', required: 'java.lang.String'">s = bo ? 1L: 2</error>;
    <error descr="Incompatible types. Found: 'byte', required: 'java.lang.String'">s = bo ? b : 2</error>;
    <error descr="Incompatible types. Found: 'int', required: 'java.lang.String'">s = bo ? b : b+2</error>;
    <error descr="Incompatible types. Found: 'long', required: 'java.lang.String'">s = bo ? b+1L : 2</error>;
    <error descr="Incompatible types. Found: 'float', required: 'java.lang.String'">s = bo ? f : f+2</error>;
    <error descr="Incompatible types. Found: 'double', required: 'java.lang.String'">s = bo ? d : 2</error>;

 }

 void cf1() {

        byte[] byteArr = new byte[10];
        boolean bool = true;
        byte i = bool ? byteArr[0] : 0;
 }


}