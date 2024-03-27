// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class SomeClass {
  enum SomeEnum {Constant, AnotherConstant}

  public static void main(String[] args) {
    <warning descr="Call to Enum.values()">SomeEnum.values()</warning>;
  }
}