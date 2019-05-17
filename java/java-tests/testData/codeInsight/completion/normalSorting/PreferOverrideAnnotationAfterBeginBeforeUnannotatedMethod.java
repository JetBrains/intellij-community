// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class Foo extends BaseClass {
  <caret>
  int dummyMethod1() {

  }
}

abstract class BaseClass {
  abstract String firstOverride();
}