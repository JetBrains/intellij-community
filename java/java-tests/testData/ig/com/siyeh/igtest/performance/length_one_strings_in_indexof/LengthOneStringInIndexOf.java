class Test {
  void simple(String s) {
    if(s.indexOf(<warning descr="\"x\" can be replaced with 'x'">"x"</warning>) > 0) {}
    if(s.indexOf((<warning descr="\"x\" can be replaced with 'x'">"x"</warning>)) > 0) {}
    if(s.indexOf(((<warning descr="\"x\" can be replaced with 'x'">"x"</warning>))) > 0) {}
    if(s.indexOf(<warning descr="\"\'\" can be replaced with '\''">"\'"</warning>) > 0) {}
    if(s.indexOf(<warning descr="\"'\" can be replaced with '\''">"'"</warning>) > 0) {}

    if(s.indexOf("//") > 0) {}
  }
}