package org.jetbrains.io.fastCgi;

import io.netty.buffer.ByteBuf;

public class FastCgiResponse {
  private final int id;
  private final ByteBuf data;

  public FastCgiResponse(int id, ByteBuf data) {
    this.id = id;
    this.data = data;
  }

  public ByteBuf getData() {
    return data;
  }

  public int getId() {
    return id;
  }
}