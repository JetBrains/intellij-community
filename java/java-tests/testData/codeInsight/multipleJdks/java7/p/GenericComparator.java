package p;

import java.util.*;
import java.util.concurrent.*;

abstract class B extends A {
  void foo() throws Exception {
    call().<error descr="Cannot resolve method 'stream()'">stream</error>();
  }
}