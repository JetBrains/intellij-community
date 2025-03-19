package com.siyeh.igtest.encapsulation;

public class PackageVisibleField
{
    int <warning descr="Package-visible field 'm_barargus'">m_barargus</warning> = -1;

}
class Outer {
  private static class Inner {
    final int test = 0;
  }
}