// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

class Test {

  int migrationField;

  public void foo() {
    StringBuffer stringBuffer = new StringBuffer();
    stringBuffer.append(migrationField);
  }
}