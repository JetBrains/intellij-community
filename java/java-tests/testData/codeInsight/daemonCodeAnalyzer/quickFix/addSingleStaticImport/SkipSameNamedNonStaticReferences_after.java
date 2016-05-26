import foo.Clazz;

import static foo.Clazz.print;

class Test {
  void bar(Clazz a){
    print();
    print(1);
    a.print("");
  }
}