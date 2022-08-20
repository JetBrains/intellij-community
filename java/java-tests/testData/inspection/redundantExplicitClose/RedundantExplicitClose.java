// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class MyAutoCloseable implements AutoCloseable {
  @Override
  public void close() {

  }
}

class C {
  public static void main(String[] args) {
    try(MyAutoCloseable ac = new MyAutoCloseable()) {
      if (args.length == 0) {
        System.out.println("No parameters");
        <warning descr="Redundant 'close()'">ac.close();</warning>
      } else if (args.length == 1) {
        System.out.println("One parameter: " + args[0]);
      } else if (args.length > 1) {
      }
    }
  }
}