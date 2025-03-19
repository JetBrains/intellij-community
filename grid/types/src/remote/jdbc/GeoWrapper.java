package com.intellij.database.remote.jdbc;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public class GeoWrapper implements Serializable {
  private final byte[] myBinary;
  private final String myWkt;

  public GeoWrapper(@NotNull byte[] binary, @NotNull String wkt){
    this.myBinary = binary;
    this.myWkt = wkt;
  }

  public String getWkt() {
    return myWkt;
  }

  public byte[] getBinary() {
    return myBinary;
  }
}