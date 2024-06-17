import <info descr="Not resolved until the project is fully loaded">com</info>.<info descr="Not resolved until the project is fully loaded">foo</info>.*;
import <info descr="Not resolved until the project is fully loaded">com</info>.<info descr="Not resolved until the project is fully loaded">bar</info>.*;

import java.util.*;
import <info descr="Not resolved until the project is fully loaded">com</info>.<info descr="Not resolved until the project is fully loaded">mypkg</info>.<info descr="Not resolved until the project is fully loaded">Timer</info>;

class Test {
  void test() {
    <info descr="Not resolved until the project is fully loaded">X</info>.<info descr="Not resolved until the project is fully loaded">m1</info>();
    <info descr="Not resolved until the project is fully loaded">Y</info>.<info descr="Not resolved until the project is fully loaded">m2</info>();

    System.out.println(<info descr="Not resolved until the project is fully loaded">Timer</info>.<info descr="Not resolved until the project is fully loaded">getSomething</info>());
    System.out.println(java.util.Timer.<error descr="Cannot resolve method 'getSomething' in 'Timer'">getSomething</error>());
  }
}