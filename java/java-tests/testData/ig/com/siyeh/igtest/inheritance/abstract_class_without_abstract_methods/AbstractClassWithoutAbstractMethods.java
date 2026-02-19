package com.siyeh.igtest.inheritance.abstract_method_overrides_abstract_method;

import java.util.*;
import org.jetbrains.annotations.Nullable;

abstract class AbstractClassWithAbstactMethod {
  public abstract Object foo() throws Exception;
  void one(String s){};
}

abstract class <warning descr="Class 'AbstractClassWithoutAbstactMethod' is declared 'abstract', and has no 'abstract' methods">AbstractClassWithoutAbstactMethod</warning> {
  void one(String s){};
}
abstract class <warning descr="Class 'AbstractClassWithoutAbstactMethod2' is declared 'abstract', and has no 'abstract' methods">AbstractClassWithoutAbstactMethod2</warning> {
}

abstract class AbstractUtilityClass {
  public static void one(String s){};
}