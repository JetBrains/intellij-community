// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class Test {
  final String s;

    {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("foo");
        stringBuilder.append("bar");
        s = stringBuilder.toString();
    }
}