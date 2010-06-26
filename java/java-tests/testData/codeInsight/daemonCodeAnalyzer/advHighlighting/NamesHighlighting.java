import <info descr="null" type="CLASS_NAME">java.io</info>.*; // highlight on demand import as class name

class <info descr="null" type="CLASS_NAME">a</info> {
 void <info descr="null" type="METHOD_DECLARATION">method</info>() {
   <info descr="null" type="METHOD_CALL">method</info>();

   new <info descr="null" type="CONSTRUCTOR_CALL">Exception</info>();
   new <info descr="null" type="CONSTRUCTOR_CALL">java.lang.Exception</info>();
 }
 <info descr="null" type="CONSTRUCTOR_DECLARATION">a</info>() {
   new <info descr="null" type="CONSTRUCTOR_CALL">a</info>();
 }
 
 /**
   * @see <info descr="null" type="INTERFACE_NAME">itf</info>#<info descr="null" type="METHOD_CALL">method</info>(<info descr="null" type="JAVA_KEYWORD">double</info>)
  */
  static void <info descr="null" type="METHOD_DECLARATION">f</info>() {
   <info descr="null" type="CLASS_NAME">Integer</info>.<info descr="null" type="STATIC_METHOD">parseInt</info>("");
   <info descr="null" type="CLASS_NAME">java.lang.Integer</info>.<info descr="null" type="STATIC_METHOD">parseInt</info>("");
   <info descr="null" type="STATIC_METHOD">f</info>();
 }

 interface <info descr="null" type="INTERFACE_NAME">itf</info>{
   int <info descr="null" type="STATIC_FIELD">CONST</info> = 0;
   /** .
     * @param <info descr="null" type="PARAMETER">d</info> Important param
     */
   void <info descr="null" type="METHOD_DECLARATION">method</info>(double <info descr="null" type="PARAMETER">d</info>);
 }
 void <info descr="null" type="METHOD_DECLARATION">ff</info>(<info descr="null" type="INTERFACE_NAME">Runnable</info> <info descr="null" type="PARAMETER">r</info>) {
   <info descr="null" type="METHOD_CALL">ff</info>(
    new <info descr="null" type="INTERFACE_NAME">java.lang.Runnable</info>()
       {
         public void <info descr="null" type="METHOD_DECLARATION">run</info>() {}
         int <info descr="null" type="INSTANCE_FIELD">instance</info> = 0;
       }
   );

   int <info descr="null" type="LOCAL_VARIABLE">i</info> = <info descr="null" type="CLASS_NAME">java.lang.Integer</info>.<info descr="null" type="STATIC_FIELD">MIN_VALUE</info>;
   int <info descr="null" type="LOCAL_VARIABLE">j</info> = <info descr="null" type="INTERFACE_NAME">itf</info>.<info descr="null" type="STATIC_FIELD">CONST</info>;
 }
}

class <info descr="null" type="CLASS_NAME">NoCtrClass</info> {
  {
    // default constructor call looks like class
    new <info descr="null" type="CLASS_NAME">NoCtrClass</info>();
  }
  void <info descr="null" type="METHOD_DECLARATION">ff</info>(int <info descr="null" type="REASSIGNED_PARAMETER">param</info>) {
    int <info descr="null" type="REASSIGNED_LOCAL_VARIABLE">i</info> = 1;
    <info descr="null" type="REASSIGNED_LOCAL_VARIABLE">i</info> ++;

    <info descr="null" type="REASSIGNED_PARAMETER">param</info> = 0;
  }
}

class <info descr="null" type="CLASS_NAME">Generic</info><<info descr="null" type="TYPE_PARAMETER_NAME">TT</info> extends <info descr="null" type="INTERFACE_NAME">Runnable</info>> {
  <info descr="null" type="TYPE_PARAMETER_NAME">TT</info> <info descr="null" type="INSTANCE_FIELD">field</info>;
}