// string literal
public class a {
  char c1 =  <error descr="Empty character literal">''</error>;
  char c2 =  <error descr="Illegal escape character in character literal">'\dd'</error>;
  char c4 =  <error descr="Too many characters in character literal">'xxx'</error>;
  char c5 =  <error descr="Too many characters in character literal">'\78'</error>;
  char c6 =  <error descr="Too many characters in character literal">'\78'</error>;

  char[] cA = new char[] { 'd','\b','\f','\n','\r'
             ,'\t','"','\\',' ','\u1234','\uFFFF'
             , '\7', '\77', '\345', '\0'};

  String s1 =  <error descr="Illegal escape character in string literal">"\xd"</error>;
  String s11=  <error descr="Illegal escape character in string literal">"\udX"</error>;
  String s12=  <error descr="Illegal escape character in string literal">"c:\TEMP\test.jar"</error>;
  String s3 = "";
  String s4 = "\u0000";

  String s5 = <error descr="Illegal escape character in string literal">"\u000d"</error>;
  String s6 = <error descr="Illegal escape character in string literal">"\u000a"</error>;
  char c7 = <error descr="Illegal escape character in character literal">'\u000d'</error>;
  char c8  = <error descr="Illegal escape character in character literal">'\u000a'</error>;


  void foo(String a) {
     foo(<error descr="Illegal line end in string literal">"aaa</error>
     );
  }

  
}
