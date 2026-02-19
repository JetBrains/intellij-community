package com.example;

public class NullabilityAnnotationOnModule {
  public static void main(String[] args) {
    if (<warning descr="Condition 'args == null' is always 'false'">args == null</warning>) return;
    System.out.println("Hello world!");
  }
}