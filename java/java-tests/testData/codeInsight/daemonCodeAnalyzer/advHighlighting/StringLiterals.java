/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// string literal
public class a {
  char c1 =  <error descr="Empty character literal">''</error>;
  char c2 =  '<error descr="Illegal escape character in character literal">\d</error>d';
  char c4 =  <error descr="Too many characters in character literal">'xxx'</error>;
  char c5 =  <error descr="Too many characters in character literal">'\78'</error>;
  char c6 =  <error descr="Too many characters in character literal">'\78'</error>;

  char[] cA = new char[] { 'd','\b','\f','\n','\r'
             ,'\t','"','\\',' ','\u1234','\uFFFF'
             , '\7', '\77', '\345', '\0', <error descr="Unclosed character literal">'x</error>
  };

  String s1 =  "<error descr="Illegal escape character in string literal">\x</error>d";
  String s11=  "<error descr="Illegal Unicode escape sequence">\ud</error>X";
  String s12=  "c:<error descr="Illegal escape character in string literal">\T</error>EMP\test.jar";
  String s3 = "";
  String s4 = "\u0000";

  String s5 = "<error descr="Illegal escape character in string literal">\u000d</error>";
  String s6 = "<error descr="Illegal escape character in string literal">\u000a</error>";
  String s7 = "<error descr="Illegal escape character in string literal">\
</error>  ";
  String s8 = "<error descr="Illegal Unicode escape sequence">\ubad</error><error descr="Illegal Unicode escape sequence">\ubad</error> <error descr="Illegal Unicode escape sequence">\ubad</error> <error descr="Illegal Unicode escape sequence">\ubad</error>";
  char c7 = '<error descr="Illegal escape character in character literal">\u000d</error>';
  char c8  = '<error descr="Illegal escape character in character literal">\u000a</error>';
  char c9 = '<error descr="Illegal Unicode escape sequence">\u</error>';
  char c10 = '<error descr="Illegal escape character in character literal">\
</error> ';

  String perverts = "\uuuuuuuuuuuu1234";
  String perverts2 = "<error descr="Illegal Unicode escape sequence">\uuuuuuuuuuuu123</error>";
  char perv2 = '\uu3264';

  String backSlash1 = <error descr="Line end not allowed in string literals">"\u005c";</error>
  String backSlash2 = "\u005c\";
  String backSlash3 = "\\u005c";
  String backSlash4 = "\u005c\u005c";
  String backSlash5 = "\u005c134";
  String backSlash6 = <error descr="Line end not allowed in string literals">"\134\u005c";</error>
  String backSlash7 = "\u005c\134";
  String backSlash8 = "\u005c\u0022";
  char backSlash9 = '\u005c\u0027';

  void foo(String a) {
     foo(<error descr="Line end not allowed in string literals">"aaa</error>
     );
  }

  String[] s = {
    <error descr="Line end not allowed in string literals">"unclosed</error>
  };
}
