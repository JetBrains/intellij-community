// "Fix all 'Enum 'switch' statement that misses case' problems in file" "false"
class Foo {
  void foo(E e) {
    switch (e){

    } <caret>
  }
}

enum E {
  E1, E2;
}