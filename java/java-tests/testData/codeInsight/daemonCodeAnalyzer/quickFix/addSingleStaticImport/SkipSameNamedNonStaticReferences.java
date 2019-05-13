import foo.Clazz;

class Test {
  void bar(Clazz a){
    Clazz.pri<caret>nt();
    Clazz.print(1);
    a.print("");
  }
}