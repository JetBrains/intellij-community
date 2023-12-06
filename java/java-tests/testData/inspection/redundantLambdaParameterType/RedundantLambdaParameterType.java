// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import java.util.List;
import java.util.Map;

class C {
    void singleParameter(List<String> list) {
      list.forEach((<warning descr="Lambda parameter type is redundant">String</warning> s) -> System.out.println("#" + s));
    }
  void twoParameters(Map<String, Integer> map) {
    map.forEach((<warning descr="Lambda parameter type is redundant">String</warning> s, <warning descr="Lambda parameter type is redundant">Integer</warning> i) -> System.out.println(s + "=" + i));
  }
}