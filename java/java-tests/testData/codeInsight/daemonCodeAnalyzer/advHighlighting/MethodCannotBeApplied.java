class A {{
  String.valueOf(<error descr="Cannot resolve symbol 'chars'">chars</error>, 0, 10); // all arguments are highlighted when only chars has a problemij
  new String(<error descr="Cannot resolve symbol 'chars'">chars</error>, 0, 10); // highlighting is good here.

  String.valueOf<error descr="'valueOf(char[], int, int)' in 'java.lang.String' cannot be applied to '(int, int, int)'">(0, 0, 10)</error>;
  new String<error descr="Cannot resolve constructor 'String(int, int, int)'">(0, 0, 10)</error>;
}}
