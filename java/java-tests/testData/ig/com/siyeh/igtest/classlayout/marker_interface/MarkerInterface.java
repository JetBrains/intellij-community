package com.siyeh.igtest.classlayout.marker_interface;

public interface <warning descr="Marker interface 'MarkerInterface'">MarkerInterface</warning> {
}
interface  <warning descr="Marker interface 'X'">X</warning><T> {}
interface  Y extends X<String> {}