// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class C {
  public static void main(String[] args) {
    StringBuilder sb = new StringBuilder();
    <warning descr="Can be replaced with 'String.repeat()'">for</warning>(int i=0; i<100; i++) {
      sb = sb.append(" ");
    }
    System.out.println("Result: " + sb);
  }
}